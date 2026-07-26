import { A, Code, H2, H3, LI, Lead, Note, P, Pre, Strong, Table, TD, TH, UL } from "../prose";

export default function WhatItCostsToSelfHostClamAv() {
  return (
    <>
      <Lead>
        ClamAV is free software, which is why the cost question usually stops there. But nobody
        deploys an antivirus engine — they deploy a scanning service, and the difference between
        those two things is where the money goes. Here is the whole model, with every assumption
        stated, so you can substitute your own numbers.
      </Lead>

      <P>
        We run ClamAV in production. The infrastructure figures below are our actual bill, not an
        estimate, which is the only part of this we can be precise about. The engineering figures are
        explicitly a model, and they are labelled as such every time they appear.
      </P>

      <H2>What &ldquo;self-hosting ClamAV&rdquo; actually means</H2>

      <P>
        ClamAV gives you <Code>clamd</Code>, a long-running daemon that listens on a socket and
        speaks a binary protocol. It is not an HTTP API. To scan a file you open a TCP connection,
        send the literal bytes <Code>zINSTREAM\0</Code>, then a sequence of length-prefixed chunks,
        then a zero-length chunk to terminate, and read back a line of text:
      </P>

      <Pre label="the actual clamd wire protocol">{`> zINSTREAM\\0
> \\x00\\x00\\x08\\x00   (chunk length, 4-byte big-endian)
> ...2048 bytes of file...
> \\x00\\x00\\x00\\x00   (zero-length chunk = end of stream)
< stream: Eicar-Test-Signature FOUND`}</Pre>

      <P>
        That is the entire interface. There is no authentication, no rate limiting, no request
        logging, no metering, no TLS, and no notion of a user. Everything you would need to put this
        in front of an application, you write.
      </P>

      <H2>Line one: infrastructure</H2>

      <P>
        This is the line everyone estimates, and it is the smallest of the three. Ours, on Azure in
        East US, at list price:
      </P>

      <Table>
        <thead>
          <tr>
            <TH>Line item</TH>
            <TH>Spec</TH>
            <TH>Monthly</TH>
          </tr>
        </thead>
        <tbody>
          <tr>
            <TD>Virtual machine</TD>
            <TD>Standard_B2ms — 2 vCPU / 8 GiB, Linux, pay-as-you-go at $0.0832/hr</TD>
            <TD>$60.74</TD>
          </tr>
          <tr>
            <TD>Static public IP</TD>
            <TD>Standard SKU, $0.005/hr</TD>
            <TD>$3.65</TD>
          </tr>
          <tr>
            <TD>OS disk</TD>
            <TD>30 GB provisioned, Premium SSD</TD>
            <TD>~$5.00</TD>
          </tr>
          <tr>
            <TD>Egress</TD>
            <TD>Scanning is inbound; the response is a few hundred bytes of JSON</TD>
            <TD>$0.00</TD>
          </tr>
          <tr>
            <TD>
              <Strong>Total</Strong>
            </TD>
            <TD>One box, running 24/7</TD>
            <TD>
              <Strong>~$70</Strong>
            </TD>
          </tr>
        </tbody>
      </Table>

      <P>
        The disk figure is approximate — Azure renders managed-disk prices dynamically and you should
        confirm it in the calculator. It is under 10% of the line and changes no conclusion.
      </P>

      <H3>Why 8 GiB and not 2</H3>

      <P>
        The instinct is to run this on the smallest box available. You cannot.{" "}
        <Code>clamd</Code> loads the entire signature database into memory and keeps it resident. On
        our production instance that is <Strong>974 MB resident, peaking at 987 MB</Strong> — call it
        a gigabyte, measured rather than estimated. That is not a cache you can trim; it is how the
        daemon works.
      </P>

      <P>
        A 1 GB instance is therefore out, and a 2 GiB one leaves very little for an operating system,
        your application and a database. Our whole stack — scanner, API, web frontend, Postgres,
        reverse proxy — sits at about 2 GB of the 8 GiB box it runs on, so 4 GiB is a realistic
        floor if you are running more than the scanner.
      </P>

      <P>
        Worse, steady state is not the peak. When <Code>freshclam</Code> pulls new signatures,{" "}
        <Code>clamd</Code> can load the new database while the old one is still resident —{" "}
        <Strong>transiently doubling</Strong> its memory. If you have not set{" "}
        <Code>ConcurrentDatabaseReload no</Code>, a routine signature update is capable of
        OOM-killing your scanner in the middle of the working day. This is the single most common way
        a self-hosted ClamAV falls over, and it is invisible until it happens.
      </P>

      <Note tone="warning" title="We had not set this either">
        When this post was first drafted, <Code>ConcurrentDatabaseReload no</Code> was not set on our
        own deployment — the only guard was a 3 GB container limit with enough headroom to absorb the
        spike. Writing this paragraph is what prompted us to fix it. We are leaving the admission in,
        because a post about operational cost that implied we had solved everything from the start
        would not be worth reading.
      </Note>

      <H2>Line two: the integration build</H2>

      <P>
        This is the real number, and it is the one that never appears in a comparison table because
        it is paid once, in salary, before anything works. Between a socket that speaks INSTREAM and
        something an application team can call, you are writing:
      </P>

      <Table>
        <thead>
          <tr>
            <TH>Layer</TH>
            <TH>What raw ClamAV gives you</TH>
          </tr>
        </thead>
        <tbody>
          <tr>
            <TD>HTTP interface</TD>
            <TD>Nothing. You write the multipart handling, streaming, and the response contract.</TD>
          </tr>
          <tr>
            <TD>Authentication</TD>
            <TD>Nothing. No users, no keys, no sessions.</TD>
          </tr>
          <tr>
            <TD>API keys</TD>
            <TD>Nothing. Issuing, hashing at rest, rotation, revocation.</TD>
          </tr>
          <tr>
            <TD>Quotas and metering</TD>
            <TD>Nothing. No counters, no periods, no enforcement.</TD>
          </tr>
          <tr>
            <TD>Rate limiting</TD>
            <TD>
              <Strong>None whatsoever</Strong> — see below.
            </TD>
          </tr>
          <tr>
            <TD>TLS</TD>
            <TD>Nothing. Certificate issuance and renewal are yours.</TD>
          </tr>
          <tr>
            <TD>Deployment</TD>
            <TD>Nothing. CI/CD, rollback, signature-volume persistence.</TD>
          </tr>
          <tr>
            <TD>GPL position</TD>
            <TD>Your counsel&rsquo;s problem, before you ship commercially.</TD>
          </tr>
        </tbody>
      </Table>

      <P>
        Call it <Strong>two to four weeks of a senior engineer</Strong> to build this properly,
        including the tests and the runbook. At a fully-loaded rate of $75&ndash;$150/hour, that is{" "}
        <Strong>$6,000&ndash;$24,000</Strong>, once.
      </P>

      <Note title="This range is a model, not research">
        We did not survey anyone. It is arithmetic on a fully-loaded senior rate and a scope estimate
        from having built the thing. Substitute your own rate and your own estimate — the shape of the
        conclusion holds across a wide range, and if your team already has this scaffolding from
        another service, your number is genuinely much smaller.
      </Note>

      <H3>The rate limiting deserves its own paragraph</H3>

      <P>
        ClamAV has no request throttling of any kind. None. If you expose <Code>clamd</Code> to
        application traffic without putting a governor in front of it, a single client — malicious,
        or just a retry loop with no backoff — can saturate the daemon and exhaust the box. Every
        other scan on that host queues behind it.
      </P>

      <P>
        The fix is not one thing, it is three: a per-IP limit at the proxy, a per-account limit in
        the application, and a global cap on concurrent scans to protect the memory footprint. Ours
        is set to four concurrent scans. That number is not from documentation; it is from finding
        out.
      </P>

      <H2>Line three: keeping it running</H2>

      <P>
        Once it is built it does not stop costing. Signature updates need watching, memory needs
        tuning, certificates renew, the base image needs patching, and the daemon occasionally needs
        restarting for reasons you will investigate at the time.
      </P>

      <P>
        In our experience this is <Strong>two to four hours a month</Strong> once things are stable,
        which at the same rate is <Strong>$150&ndash;$600/month</Strong>. It is lumpy rather than
        steady — several quiet months, then an afternoon gone to a reload that OOM-killed the daemon.
      </P>

      <H2>Adding it up</H2>

      <Table>
        <thead>
          <tr>
            <TH>Basis</TH>
            <TH>Infrastructure</TH>
            <TH>Engineering</TH>
            <TH>Total</TH>
          </tr>
        </thead>
        <tbody>
          <tr>
            <TD>Per month, ongoing</TD>
            <TD>~$70</TD>
            <TD>$150&ndash;$600 of operations time</TD>
            <TD>
              <Strong>$220&ndash;$670</Strong>
            </TD>
          </tr>
          <tr>
            <TD>Year one, including the build</TD>
            <TD>~$840</TD>
            <TD>$6,000&ndash;$24,000 once, plus $1,800&ndash;$7,200 of operations</TD>
            <TD>
              <Strong>$8,640&ndash;$32,040</Strong>
            </TD>
          </tr>
        </tbody>
      </Table>

      <P>
        The headline is that <Strong>infrastructure is almost never the expensive part</Strong>. At
        the low end it is under a third of the monthly cost and under a tenth of year one. If you
        budgeted for a $70 VM and called that the cost of self-hosting, you were off by somewhere
        between 3x and 460x, depending on how you count.
      </P>

      <H2>When self-hosting is the right answer anyway</H2>

      <P>
        Cost is one input and frequently not the deciding one. Run it yourself if:
      </P>

      <UL>
        <LI>
          <Strong>Files cannot leave your environment.</Strong> Data residency rules, a regulated
          perimeter, or a customer contract that forbids it. This is a hard constraint and no amount
          of arithmetic moves it.
        </LI>
        <LI>
          <Strong>You are air-gapped or on-premises.</Strong> Then a hosted API is not a candidate at
          all.
        </LI>
        <LI>
          <Strong>Your volume is genuinely large.</Strong> Cost per scan on a fixed box falls
          forever. At millions of scans a month, running your own hardware wins on price and it is
          not close.
        </LI>
        <LI>
          <Strong>You need detection beyond ClamAV.</Strong> Self-hosting lets you chain engines,
          add YARA rules, or layer commercial scanners. A single-engine API cannot.
        </LI>
        <LI>
          <Strong>You already run ClamAV.</Strong> The build cost — the dominant term above — is
          already sunk. Your marginal cost is the operations line and nothing else.
        </LI>
      </UL>

      <P>
        That last one matters more than it looks. Most of this post&rsquo;s cost model is a one-time
        charge. If you paid it two years ago, the calculation is completely different, and switching
        to anything is mostly a migration cost with no offsetting saving.
      </P>

      <H2>The comparison, since you can do the arithmetic anyway</H2>

      <P>
        We sell this as a hosted API. Our Pro tier is $39/month for 100,000 scans, so against the
        model above: <Strong>roughly 6&ndash;17x</Strong> on ongoing monthly cost, or{" "}
        <Strong>18&ndash;68x</Strong> across year one, where the integration build you never do is
        counted. Two different numbers measuring two different things, and anyone quoting the larger
        one at you without saying which is being careless or worse.
      </P>

      <P>
        We should be equally clear about what that does not buy. The detection is{" "}
        <Strong>identical</Strong>, because it is the same ClamAV — no additional engines, no YARA,
        no content disarm and reconstruction, no hash reputation. If you are self-hosting today and
        happy with it, the honest recommendation is to set{" "}
        <Code>ConcurrentDatabaseReload no</Code>, put a concurrency cap in front of{" "}
        <Code>clamd</Code>, and carry on.
      </P>

      <Note title="Where these numbers come from">
        Infrastructure is our real Azure bill at list price, retrieved 25 July 2026. Engineering
        figures are a stated model on a $75&ndash;$150/hour fully-loaded senior rate, 2&ndash;4 weeks
        of build and 2&ndash;4 hours a month of operations. Everything about ClamAV&rsquo;s behaviour
        — the resident signature database, the reload spike, the absence of rate limiting — is from
        running it, and is verifiable in ClamAV&rsquo;s own documentation.
      </Note>
    </>
  );
}
