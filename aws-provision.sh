#!/usr/bin/env bash
#
# Provision the AWS side of the geneav stack: one Lightsail instance, three ECR
# repositories, the IAM identities CI and the box need, and an S3 bucket for
# off-box database backups.
#
# Prereqs: `aws configure` already done with an admin-ish identity, and the
# region below available to it.
#
# Usage:   ./aws-provision.sh [--dry-run] [--allow-ssh-from <cidr>]
#
# Idempotent-ish: every create is guarded by a lookup, so re-running fills in
# what is missing rather than erroring out. The two exceptions are the SSM
# activation and the backup user's access key, which are one-shot secrets — the
# script prints them once and cannot print them again.
#
# WHY LIGHTSAIL. The stack measures ~2.0 GB resident (docs/business-case.md),
# so the 4 GB bundle is a comfortable fit. The equivalent EC2 build —
# t4g.medium + 40 GB gp3 + an IPv4 address — costs ~$31/month before you have
# done anything, and Graviton would force multi-arch image builds that CI does
# not do today. Lightsail bundles compute, 80 GB SSD, the static IP and 4 TB of
# transfer into one x86 price.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---- local values ----
# .env.aws carries the account id, the derived ECR/bucket names, and the one-shot
# secrets from a previous run. It is gitignored and absent in CI, which passes
# the same values in through the environment instead — hence the -f guard.
ENV_FILE="${GENEAV_ENV_FILE:-$SCRIPT_DIR/.env.aws}"
if [ -f "$ENV_FILE" ]; then
  echo "==> reading $ENV_FILE"
  set -a; . "$ENV_FILE"; set +a
fi

# ---- configuration (override via env, e.g. REGION=eu-west-1 ./aws-provision.sh) ----
REGION="${AWS_REGION:-us-east-1}"
AZ="${AZ:-${REGION}a}"
INSTANCE="${INSTANCE:-geneav}"
STATIC_IP_NAME="${STATIC_IP_NAME:-geneav-ip}"
# medium_3_0 = 4 GB RAM / 2 vCPU / 80 GB SSD / 4 TB transfer.
# Do NOT drop to small_3_0 (2 GB): clamd alone holds ~974 MB resident and a
# freshclam reload needs headroom on top. Confirm the id with
# `aws lightsail get-bundles --region $REGION` — bundle ids are versioned.
BUNDLE="${BUNDLE:-medium_3_0}"
BLUEPRINT="${BLUEPRINT:-ubuntu_22_04}"
REMOTE_DIR="${GENEAV_REMOTE_DIR:-/home/ubuntu/geneav}"

GITHUB_REPO="${GITHUB_REPO:-sanaloha/geneav-aws}"
GITHUB_REF="${GITHUB_REF_NAME:-main}"
# GitHub now issues OIDC subjects carrying IMMUTABLE numeric ids:
#   repo:<owner>@<owner_id>/<repo>@<repo_id>:ref:refs/heads/<branch>
# rather than the name-only form every tutorial still shows. A trust policy
# pinned to the old shape silently never matches, and STS reports only
# "Not authorized to perform sts:AssumeRoleWithWebIdentity" — the actual subject
# is visible in CloudTrail under userIdentity.userName, which is how this was
# found on 2 Aug 2026. Looked up automatically when `gh` is available; override
# here if it is not.
GITHUB_OWNER_ID="${GITHUB_OWNER_ID:-}"
GITHUB_REPO_ID="${GITHUB_REPO_ID:-}"

CI_ROLE="${CI_ROLE:-geneav-ci}"
SSM_ROLE="${SSM_ROLE:-geneav-ssm-instance}"
BACKUP_USER="${BACKUP_USER:-geneav-backup-writer}"

DRY_RUN=0
ALLOW_SSH_FROM=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --allow-ssh-from) ALLOW_SSH_FROM="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

# Swallows stdout: every call routed through here is a create/put whose JSON
# response we do not read. stderr is deliberately left alone so failures are
# still visible. (Note for anyone porting more of the old Azure script: there is
# no `--output none` in the AWS CLI — it only accepts json/text/table/yaml —
# so quieting a command is a redirection here, not a flag.)
run() {
  if [ "$DRY_RUN" = "1" ]; then printf '  [dry-run] %s\n' "$*"; else "$@" >/dev/null; fi
}
aws_() { aws --region "$REGION" "$@"; }

# Persist a value back into $ENV_FILE, replacing the key in place if present.
#
# This exists for the two ONE-SHOT secrets — the SSM activation code and the
# backup user's secret access key. AWS prints each exactly once and will never
# reissue it, so relying on the operator to copy them out of terminal scrollback
# is a bad trade against writing them to a file that is already gitignored.
# Placeholder markers are skipped so a dry run cannot blank real values.
save_env() {
  [ "$DRY_RUN" = "1" ] && return 0
  [ -f "$ENV_FILE" ] || return 0
  case "${2:-}" in ''|'<dry-run>'|'<unchanged>') return 0 ;; esac
  python3 - "$ENV_FILE" "$1" "$2" <<'PY'
import io, sys
path, key, val = sys.argv[1], sys.argv[2], sys.argv[3]
lines = io.open(path, encoding="utf-8").read().splitlines(True)
out, done = [], False
for ln in lines:
    if not ln.lstrip().startswith("#") and ln.split("=", 1)[0].strip() == key:
        out.append("%s=%s\n" % (key, val))
        done = True
    else:
        out.append(ln)
if not done:
    if out and not out[-1].endswith("\n"):
        out.append("\n")
    out.append("%s=%s\n" % (key, val))
io.open(path, "w", encoding="utf-8").write("".join(out))
PY
}

# A dry run must be readable on a laptop with no AWS CLI and no credentials —
# that is most of its value, since the point is to review what this will create
# BEFORE handing it an account. Only the real run insists on either.
if ! command -v aws >/dev/null 2>&1; then
  if [ "$DRY_RUN" = "1" ]; then
    echo "==> NOTE: aws CLI not installed; dry run will use placeholder values."
  else
    echo "FATAL: aws CLI v2 not found. Install it, or re-run with --dry-run." >&2
    exit 1
  fi
fi

EXPECTED_ACCOUNT="${AWS_ACCOUNT_ID:-}"
LIVE_ACCOUNT="$(aws sts get-caller-identity --query Account --output text 2>/dev/null || true)"

# Provisioning into the wrong account is expensive and tedious to unpick — it
# creates billable infrastructure and IAM identities somewhere nobody is looking
# for them. If the file names an account and the credentials resolve to a
# different one, that is a misconfigured profile, not something to guess past.
if [ -n "$LIVE_ACCOUNT" ] && [ -n "$EXPECTED_ACCOUNT" ] && [ "$LIVE_ACCOUNT" != "$EXPECTED_ACCOUNT" ]; then
  echo "FATAL: authenticated as account $LIVE_ACCOUNT, but $ENV_FILE expects $EXPECTED_ACCOUNT." >&2
  echo "       Switch profile (AWS_PROFILE=...) or fix AWS_ACCOUNT_ID. Refusing to continue." >&2
  exit 1
fi

ACCOUNT_ID="${LIVE_ACCOUNT:-$EXPECTED_ACCOUNT}"
if [ -z "$ACCOUNT_ID" ]; then
  if [ "$DRY_RUN" = "1" ]; then
    ACCOUNT_ID="000000000000"
    echo "==> NOTE: not authenticated; using placeholder account $ACCOUNT_ID."
  else
    echo "FATAL: could not resolve the AWS account. Run 'aws configure sso' then 'aws sso login'." >&2
    exit 1
  fi
elif [ -z "$LIVE_ACCOUNT" ]; then
  echo "==> NOTE: not authenticated; using account $ACCOUNT_ID from $ENV_FILE."
fi
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
echo "==> account $ACCOUNT_ID, region $REGION"

# ---------------------------------------------------------------- 1. ECR ----
# IMMUTABLE tags: a deploy is a pull of a known artifact and a rollback is
# re-pointing at the previous commit SHA. That only holds if a tag cannot be
# quietly repointed at different bytes.
# geneav-caddy is here because the reverse proxy needs a custom image (the
# rate-limit plugin is compiled in), and the box builds nothing — see the note in
# docker-compose.prod.yml.
for repo in geneav-backend geneav-frontend geneav-caddy; do
  if aws_ ecr describe-repositories --repository-names "$repo" >/dev/null 2>&1; then
    echo "==> ECR repo $repo already exists"
  else
    echo "==> creating ECR repo $repo"
    run aws_ ecr create-repository \
      --repository-name "$repo" \
      --image-tag-mutability IMMUTABLE \
      --image-scanning-configuration scanOnPush=true \
     
  fi
  # Without this, every deploy leaves a ~700 MB image behind forever and the
  # $0.30/month registry line quietly becomes the second-biggest bill item.
  # Five tags is enough depth to roll back through a bad afternoon.
  echo "==> lifecycle policy on $repo (keep newest 5)"
  run aws_ ecr put-lifecycle-policy --repository-name "$repo" \
    --lifecycle-policy-text '{"rules":[{"rulePriority":1,"description":"keep newest 5 images","selection":{"tagStatus":"any","countType":"imageCountMoreThan","countNumber":5},"action":{"type":"expire"}}]}'
done

# ------------------------------------------------------ 2. S3 backup bucket ----
# The postgres-backup sidecar already dumps nightly, but to a Docker volume on
# the same host as the database it protects — which is no protection at all
# against losing the box. This bucket is where those dumps go now.
BUCKET="${BACKUP_BUCKET:-geneav-backups-${ACCOUNT_ID}}"
if aws_ s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
  echo "==> bucket $BUCKET already exists"
else
  echo "==> creating bucket $BUCKET"
  # us-east-1 is the one region that rejects --create-bucket-configuration.
  if [ "$REGION" = "us-east-1" ]; then
    run aws_ s3api create-bucket --bucket "$BUCKET"
  else
    run aws_ s3api create-bucket --bucket "$BUCKET" \
      --create-bucket-configuration "LocationConstraint=$REGION"
  fi
fi
echo "==> hardening bucket $BUCKET"
run aws_ s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
run aws_ s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
# 14 days, matching the sidecar's local retention — and, more importantly, the
# published privacy policy, which tells users a deleted record "can persist in a
# backup for up to 14 days" (frontend/app/privacy/page.tsx). A longer lifecycle
# here would quietly make that statement false, so do not raise it without
# changing the policy first. Dumps are a few MB, so this is worth pennies.
run aws_ s3api put-bucket-lifecycle-configuration --bucket "$BUCKET" \
  --lifecycle-configuration '{"Rules":[{"ID":"expire-dumps","Status":"Enabled","Filter":{"Prefix":""},"Expiration":{"Days":14}}]}'

# --------------------------------------------- 3. IAM: GitHub OIDC + CI role ----
# No long-lived AWS key in GitHub. The runner mints an OIDC token, STS swaps it
# for short-lived credentials, and the trust policy below is what stops any
# other repo doing the same.
OIDC_ARN="arn:aws:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1; then
  echo "==> GitHub OIDC provider already registered"
else
  echo "==> registering GitHub OIDC provider"
  # AWS validates the endpoint against its own trusted CA store and ignores the
  # thumbprint, but the API still requires the parameter to be present.
  run aws iam create-open-id-connect-provider \
    --url https://token.actions.githubusercontent.com \
    --client-id-list sts.amazonaws.com \
    --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1 \
   
fi

# Resolve the numeric ids so the immutable subject form can be pinned. They are
# public repository metadata, not secrets.
if [ -z "$GITHUB_OWNER_ID" ] || [ -z "$GITHUB_REPO_ID" ]; then
  if command -v gh >/dev/null 2>&1; then
    GITHUB_OWNER_ID="${GITHUB_OWNER_ID:-$(gh api "repos/${GITHUB_REPO}" --jq .owner.id 2>/dev/null || true)}"
    GITHUB_REPO_ID="${GITHUB_REPO_ID:-$(gh api "repos/${GITHUB_REPO}" --jq .id 2>/dev/null || true)}"
  fi
fi

# `sub` is pinned to one repo AND one ref. A wildcard here would let any branch
# — including one from a fork's PR — push images and run commands on the box.
#
# BOTH subject shapes are listed because GitHub is mid-migration and a StringEquals
# list matches if ANY entry does. Neither entry contains a wildcard, so the pin to
# this repo and this branch holds either way. The id-bearing form is the stronger
# of the two: delete this repo and recreate it under the same name and the ids
# differ, so the old trust no longer applies.
if [ -n "$GITHUB_OWNER_ID" ] && [ -n "$GITHUB_REPO_ID" ]; then
  SUB_LIST="\"repo:${GITHUB_REPO%%/*}@${GITHUB_OWNER_ID}/${GITHUB_REPO#*/}@${GITHUB_REPO_ID}:ref:refs/heads/${GITHUB_REF}\",
        \"repo:${GITHUB_REPO}:ref:refs/heads/${GITHUB_REF}\""
else
  echo "==> WARNING: could not resolve GitHub owner/repo ids (gh not installed or"
  echo "    not authenticated). Falling back to the name-only OIDC subject, which"
  echo "    GitHub may no longer send — CI would then fail with AccessDenied on"
  echo "    sts:AssumeRoleWithWebIdentity. Set GITHUB_OWNER_ID / GITHUB_REPO_ID."
  SUB_LIST="\"repo:${GITHUB_REPO}:ref:refs/heads/${GITHUB_REF}\""
fi

CI_TRUST=$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": { "Federated": "$OIDC_ARN" },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": [
        $SUB_LIST
        ]
      }
    }
  }]
}
JSON
)
if aws iam get-role --role-name "$CI_ROLE" >/dev/null 2>&1; then
  echo "==> role $CI_ROLE exists — refreshing trust policy"
  run aws iam update-assume-role-policy --role-name "$CI_ROLE" \
    --policy-document "$CI_TRUST"
else
  echo "==> creating role $CI_ROLE"
  run aws iam create-role --role-name "$CI_ROLE" \
    --description "GitHub Actions: push images to ECR and deploy via SSM" \
    --assume-role-policy-document "$CI_TRUST"
fi

# ecr:GetAuthorizationToken cannot be resource-scoped — it is account-wide by
# design. Everything that actually moves bytes is scoped to the three repos.
#
# The SSM instance ARN is a wildcard because the managed-node id (mi-…) does not
# exist until the agent registers, which happens after this script runs. Once
# you have it, tighten managed-instance/* to that one id.
CI_POLICY=$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    { "Sid": "EcrAuth", "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    { "Sid": "EcrPush", "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability", "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload", "ecr:PutImage", "ecr:UploadLayerPart",
        "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:DescribeImages"
      ],
      "Resource": [
        "arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/geneav-backend",
        "arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/geneav-frontend",
        "arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/geneav-caddy"
      ] },
    { "Sid": "SsmDeploy", "Effect": "Allow", "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ssm:${REGION}::document/AWS-RunShellScript",
        "arn:aws:ssm:${REGION}:${ACCOUNT_ID}:managed-instance/*"
      ] },
    { "Sid": "SsmPoll", "Effect": "Allow",
      "Action": ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations", "ssm:DescribeInstanceInformation"],
      "Resource": "*" }
  ]
}
JSON
)
run aws iam put-role-policy --role-name "$CI_ROLE" \
  --policy-name geneav-ci-deploy --policy-document "$CI_POLICY"

# ------------------------------------- 4. IAM: SSM instance role + activation ----
# Lightsail instances cannot carry an EC2 instance profile, so the box joins
# Systems Manager as a HYBRID managed node. The activation binds it to this
# role, and the agent then keeps refreshed credentials for it on disk — which is
# what lets `aws ecr get-login-password` work on the box with no stored key.
SSM_TRUST='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ssm.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
if aws iam get-role --role-name "$SSM_ROLE" >/dev/null 2>&1; then
  echo "==> role $SSM_ROLE already exists"
else
  echo "==> creating role $SSM_ROLE"
  run aws iam create-role --role-name "$SSM_ROLE" \
    --description "Lightsail box: SSM managed node + pull-only ECR" \
    --assume-role-policy-document "$SSM_TRUST"
fi
run aws iam attach-role-policy --role-name "$SSM_ROLE" \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

# PULL ONLY, all three repositories. The box never needs to write to the
# registry, and a pull-only credential means a compromise of it cannot push a
# poisoned image.
SSM_ECR_POLICY=$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": "ecr:GetAuthorizationToken", "Resource": "*" },
    { "Effect": "Allow",
      "Action": ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:BatchCheckLayerAvailability"],
      "Resource": [
        "arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/geneav-backend",
        "arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/geneav-frontend",
        "arn:aws:ecr:${REGION}:${ACCOUNT_ID}:repository/geneav-caddy"
      ] }
  ]
}
JSON
)
run aws iam put-role-policy --role-name "$SSM_ROLE" \
  --policy-name geneav-ecr-pull --policy-document "$SSM_ECR_POLICY"

echo "==> creating SSM hybrid activation"
# registration-limit 1 so a leaked activation code cannot enrol a second box.
# Standard tier is free; do NOT switch this account to the advanced-instances
# tier, which bills ~$5/month per node and would undo most of the saving.
if [ "$DRY_RUN" = "1" ]; then
  echo "  [dry-run] aws ssm create-activation --iam-role $SSM_ROLE"
  ACTIVATION_ID="<dry-run>"; ACTIVATION_CODE="<dry-run>"
else
  # IAM is eventually consistent, and SSM validates the role by name against its
  # own view of it. On a first run the role is seconds old, so create-activation
  # fails with "Nonexistent role or missing ssm service principal in trust
  # policy" even though the role is present and correct. Observed on 2 Aug 2026;
  # a retry a few seconds later succeeds. Retry rather than sleep blindly.
  ACTIVATION_JSON=""
  for attempt in $(seq 1 10); do
    if ACTIVATION_JSON="$(aws_ ssm create-activation \
      --default-instance-name "$INSTANCE" \
      --iam-role "$SSM_ROLE" \
      --registration-limit 1 \
      --description "geneav Lightsail box" 2>/dev/null)"; then
      break
    fi
    echo "  role not visible to SSM yet (attempt $attempt/10) — waiting 10s"
    ACTIVATION_JSON=""
    sleep 10
  done
  if [ -z "$ACTIVATION_JSON" ]; then
    echo "FATAL: create-activation still failing after ~100s. Re-run the error" >&2
    echo "       without 2>/dev/null to see it: aws ssm create-activation \\" >&2
    echo "         --region $REGION --iam-role $SSM_ROLE --registration-limit 1" >&2
    exit 1
  fi
  ACTIVATION_ID="$(printf '%s' "$ACTIVATION_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["ActivationId"])')"
  ACTIVATION_CODE="$(printf '%s' "$ACTIVATION_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["ActivationCode"])')"
fi

# --------------------------------------- 5. IAM: backup writer for the sidecar ----
# The backup sidecar is a plain postgres:16-alpine container with no agent, so
# it cannot borrow the node's credentials. It gets its own user, and that user
# can ONLY put objects — it cannot list, read or delete what it has written, so
# a compromised box cannot use it to shred the backups.
if aws iam get-user --user-name "$BACKUP_USER" >/dev/null 2>&1; then
  echo "==> IAM user $BACKUP_USER already exists (not rotating its key)"
  BACKUP_KEY_ID="<unchanged>"; BACKUP_KEY_SECRET="<unchanged>"
else
  echo "==> creating IAM user $BACKUP_USER"
  run aws iam create-user --user-name "$BACKUP_USER"
  if [ "$DRY_RUN" = "1" ]; then
    BACKUP_KEY_ID="<dry-run>"; BACKUP_KEY_SECRET="<dry-run>"
  else
    KEY_JSON="$(aws iam create-access-key --user-name "$BACKUP_USER")"
    BACKUP_KEY_ID="$(printf '%s' "$KEY_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["AccessKey"]["AccessKeyId"])')"
    BACKUP_KEY_SECRET="$(printf '%s' "$KEY_JSON" | python3 -c 'import json,sys;print(json.load(sys.stdin)["AccessKey"]["SecretAccessKey"])')"
  fi
fi
run aws iam put-user-policy --user-name "$BACKUP_USER" --policy-name geneav-backup-put \
  --policy-document "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"s3:PutObject\",\"Resource\":\"arn:aws:s3:::${BUCKET}/*\"}]}"

# ------------------------------------------------ 6. Lightsail instance + IP ----
# user-data runs once, as root, on first boot. It installs Docker, installs the
# SSM agent and registers it against the activation minted above — so the box
# joins Systems Manager before anyone has to SSH into it.
USER_DATA=$(cat <<EOF
#!/bin/bash
set -eux

curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu
install -d -o ubuntu -g ubuntu ${REMOTE_DIR}

# AWS CLI v2. The distro package is v1 and too old for \`ecr get-login-password\`
# to be relied on.
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
apt-get update -y && apt-get install -y unzip
unzip -q /tmp/awscliv2.zip -d /tmp && /tmp/aws/install && rm -rf /tmp/aws /tmp/awscliv2.zip

# The Lightsail Ubuntu blueprint may ship the snap build, which does not take a
# hybrid registration cleanly. Use the deb.
snap remove amazon-ssm-agent 2>/dev/null || true
curl -fsSL "https://s3.${REGION}.amazonaws.com/amazon-ssm-${REGION}/latest/debian_amd64/amazon-ssm-agent.deb" \
  -o /tmp/amazon-ssm-agent.deb
dpkg -i /tmp/amazon-ssm-agent.deb
systemctl stop amazon-ssm-agent || true
amazon-ssm-agent -register -code "${ACTIVATION_CODE}" -id "${ACTIVATION_ID}" -region "${REGION}" -y
systemctl enable --now amazon-ssm-agent
EOF
)

if aws_ lightsail get-instance --instance-name "$INSTANCE" >/dev/null 2>&1; then
  echo "==> Lightsail instance $INSTANCE already exists — leaving it alone"
else
  echo "==> creating Lightsail instance $INSTANCE ($BUNDLE in $AZ)"
  run aws_ lightsail create-instances \
    --instance-names "$INSTANCE" \
    --availability-zone "$AZ" \
    --blueprint-id "$BLUEPRINT" \
    --bundle-id "$BUNDLE" \
    --user-data "$USER_DATA"
  echo "==> waiting for $INSTANCE to report running"
  if [ "$DRY_RUN" != "1" ]; then
    for _ in $(seq 1 60); do
      state="$(aws_ lightsail get-instance --instance-name "$INSTANCE" \
        --query 'instance.state.name' --output text 2>/dev/null || echo pending)"
      [ "$state" = "running" ] && break
      sleep 5
    done
  fi
fi

if aws_ lightsail get-static-ip --static-ip-name "$STATIC_IP_NAME" >/dev/null 2>&1; then
  echo "==> static IP $STATIC_IP_NAME already allocated"
else
  echo "==> allocating static IP $STATIC_IP_NAME"
  run aws_ lightsail allocate-static-ip --static-ip-name "$STATIC_IP_NAME"
fi
# Free while attached to a running instance, billed if left dangling — so the
# attach is not optional bookkeeping.
run aws_ lightsail attach-static-ip \
  --static-ip-name "$STATIC_IP_NAME" --instance-name "$INSTANCE"

# ------------------------------------------------------- 7. instance firewall ----
# put-instance-public-ports REPLACES the whole rule set rather than adding to
# it. That is exactly what is wanted here: it is what removes the default
# world-open port 22 rule that Lightsail creates.
#
# This firewall is the only thing standing between the internet and the app
# ports Compose publishes on the host, so keep the set minimal.
PORTS=(
  "fromPort=80,toPort=80,protocol=TCP,cidrs=0.0.0.0/0"
  "fromPort=443,toPort=443,protocol=TCP,cidrs=0.0.0.0/0"
)
if [ -n "$ALLOW_SSH_FROM" ]; then
  echo "==> break-glass: allowing SSH from $ALLOW_SSH_FROM"
  PORTS+=("fromPort=22,toPort=22,protocol=TCP,cidrs=$ALLOW_SSH_FROM")
fi
echo "==> setting instance firewall (${#PORTS[@]} rules)"
run aws_ lightsail put-instance-public-ports \
  --instance-name "$INSTANCE" --port-infos "${PORTS[@]}"

# --------------------------------------------------------------- 8. summary ----
if [ "$DRY_RUN" = "1" ]; then
  echo; echo "==> --dry-run: nothing was created."; exit 0
fi

PUBIP="$(aws_ lightsail get-static-ip --static-ip-name "$STATIC_IP_NAME" \
  --query 'staticIp.ipAddress' --output text)"
NIPIO="geneav.${PUBIP//./-}.nip.io"

# Capture everything worth keeping before printing it, so a closed terminal or a
# truncated scrollback cannot cost you the one-shot values.
save_env AWS_ACCOUNT_ID "$ACCOUNT_ID"
save_env AWS_REGION "$REGION"
save_env ECR_REGISTRY "$ECR_REGISTRY"
save_env GENEAV_REGISTRY "$ECR_REGISTRY"
save_env AWS_ROLE_ARN "arn:aws:iam::${ACCOUNT_ID}:role/${CI_ROLE}"
save_env BACKUP_BUCKET "$BUCKET"
save_env GENEAV_STATIC_IP "$PUBIP"
save_env GENEAV_HOST "$NIPIO"
save_env SSM_ACTIVATION_ID "$ACTIVATION_ID"
save_env SSM_ACTIVATION_CODE "$ACTIVATION_CODE"
save_env BACKUP_AWS_ACCESS_KEY_ID "$BACKUP_KEY_ID"
save_env BACKUP_AWS_SECRET_ACCESS_KEY "$BACKUP_KEY_SECRET"
chmod 600 "$ENV_FILE" 2>/dev/null || true

cat <<EOF

============================================================
  AWS side is provisioned.

  All of the below has also been written to $ENV_FILE
  (gitignored, chmod 600) — including the two one-shot secrets, so you do
  not have to rescue them from this scrollback.

  Static IP     : $PUBIP
  Temp hostname : $NIPIO      (use this until DNS is cut over)
  ECR registry  : $ECR_REGISTRY
  Backup bucket : $BUCKET

  SSM activation (ONE-SHOT — it cannot be printed again):
    id   : $ACTIVATION_ID
    code : $ACTIVATION_CODE
  The instance registers itself with these on first boot. If registration
  failed, re-run the register command from user-data by hand.

  Backup user access key (ONE-SHOT):
    AWS_ACCESS_KEY_ID     : $BACKUP_KEY_ID
    AWS_SECRET_ACCESS_KEY : $BACKUP_KEY_SECRET

  Next:
    1) Wait for the node to appear, then record its mi- id — this is the one
       value the script cannot capture for you, since the box registers itself
       minutes after this run finishes:
         aws ssm describe-instance-information --region $REGION \\
           --query 'InstanceInformationList[].[InstanceId,ComputerName,PingStatus]' --output table
       Put it in $ENV_FILE as GENEAV_SSM_NODE.
    2) Set these in GitHub (Settings -> Secrets and variables -> Actions):
         secret   AWS_ROLE_ARN     = arn:aws:iam::${ACCOUNT_ID}:role/${CI_ROLE}
         variable AWS_REGION       = $REGION
         variable ECR_REGISTRY     = $ECR_REGISTRY
         variable GENEAV_SSM_NODE  = <the mi-... id from step 1>
         variable NEXT_PUBLIC_API_BASE_URL = https://$NIPIO
    3) Put .env.prod on the box at ${REMOTE_DIR}/.env.prod
       (copy .env.prod.example; set GENEAV_HOST=$NIPIO, POSTGRES_PASSWORD,
        UMAMI_APP_SECRET, BACKUP_S3_BUCKET=$BUCKET and the backup key above):
         aws ssm start-session --target <mi-...> --region $REGION
    4) Push to main. CI builds, pushes to ECR and deploys via SSM.
    5) Verify on https://$NIPIO before touching geneav.com DNS.
============================================================
EOF
