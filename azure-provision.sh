#!/usr/bin/env bash
#
# Provision a single Azure VM for the geneav stack.
# Prereqs: `az login` already done, correct subscription selected.
# Usage:   ./azure-provision.sh
#
set -euo pipefail

# ---- configuration (override via env, e.g. RG=my-rg ./azure-provision.sh) ----
RG="${RG:-geneav-rg}"
LOC="${LOC:-eastus}"
VM="${VM:-geneav-vm}"
# ⚠️ PRODUCTION DOES NOT MATCH THIS DEFAULT. geneav-vm is a Standard_D2s_v3
# (2 vCPU / 8 GiB, fixed performance, $70.08/mo), not a B2ms (same specs but
# burstable, $60.74/mo). Re-running this script would build a different, cheaper
# machine than the one running today. Measured 27 July 2026; the discrepancy is
# unexplained — prod was probably created by hand. Decide which you want before
# relying on either: see docs/business-case.md §5.1.
SIZE="${SIZE:-Standard_B2ms}"          # 2 vCPU / 8 GiB — needed for ClamAV's resident DB
ADMIN="${ADMIN:-azureuser}"
IMAGE="${IMAGE:-Ubuntu2204}"

# Lock SSH to the machine running this script.
MYIP="$(curl -fsS https://ifconfig.me)"
echo "Restricting SSH (22) to your current IP: $MYIP"

# ---- 1. resource group ----
az group create -n "$RG" -l "$LOC" -o none
echo "Resource group '$RG' ready in $LOC."

# ---- 2. VM with a STATIC public IP ----
az vm create \
  -g "$RG" -n "$VM" \
  --image "$IMAGE" \
  --size "$SIZE" \
  --admin-username "$ADMIN" \
  --generate-ssh-keys \
  --public-ip-sku Standard \
  --public-ip-address-allocation static \
  --os-disk-size-gb 30 \
  -o none
echo "VM '$VM' ($SIZE) created."

# ---- 3. firewall (NSG): 80 + 443 open to the world, 22 only to you ----
az vm open-port -g "$RG" -n "$VM" --port 80  --priority 1001 -o none
az vm open-port -g "$RG" -n "$VM" --port 443 --priority 1002 -o none
az network nsg rule create -g "$RG" \
  --nsg-name "${VM}NSG" --name AllowSSHFromMe \
  --priority 1000 --access Allow --protocol Tcp \
  --destination-port-ranges 22 --source-address-prefixes "$MYIP" -o none
echo "NSG rules applied (80/443 public, 22 -> $MYIP)."

# ---- 4. derive the nip.io hostname from the static public IP ----
PUBIP="$(az vm show -g "$RG" -n "$VM" -d --query publicIps -o tsv)"
HOST="geneav.${PUBIP//./-}.nip.io"

cat <<EOF

============================================================
  VM ready.
  Public IP : $PUBIP
  Hostname  : $HOST
  SSH       : ssh ${ADMIN}@${PUBIP}

  Next:
    1) ssh in and install Docker:
         curl -fsSL https://get.docker.com | sudo sh
         sudo usermod -aG docker \$USER && newgrp docker
    2) get the code onto the VM (git clone / scp)
    3) echo "GENEAV_HOST=$HOST" > .env.prod
    4) docker compose --env-file .env.prod \\
         -f docker-compose.yml -f docker-compose.prod.yml up -d --build
    5) open  https://$HOST
============================================================
EOF
