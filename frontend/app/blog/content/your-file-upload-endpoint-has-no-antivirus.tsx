import { A, Code, H2, H3, LI, Lead, Note, P, Pre, Strong, UL } from "../prose";

export default function YourFileUploadEndpointHasNoAntivirus() {
  return (
    <>
      <Lead>
        Most organisations buy antivirus per device. That model has an edge, and the edge is the
        device. A PDF that a customer uploads to your API arrives on no device at all — it lands in
        a container, gets streamed to object storage, and is read back later by a worker. At no
        point does it touch a laptop with an agent on it.
      </Lead>

      <P>
        This is not a misconfiguration you can fix in a console. It is a structural gap: the
        deployment model of endpoint antivirus and the deployment model of a cloud upload pipeline
        do not intersect.
      </P>

      <H2>The gap is structural, not an oversight</H2>

      <P>
        Endpoint antivirus works by installing an agent that hooks the filesystem. It watches writes,
        scans on access, quarantines what it does not like, and reports to a management console. All
        of that depends on there being a persistent machine to install onto and a filesystem worth
        hooking.
      </P>

      <P>
        A Lambda handling an upload cannot run an endpoint agent. Neither can a scale-to-zero
        container, a managed queue consumer, or a pre-signed upload that goes from the browser
        straight to a bucket without your code touching the bytes at all. In each case the file
        crosses your trust boundary without passing anything that scans it.
      </P>

      <P>
        So the honest description of most upload pipelines is not <em>&ldquo;under-protected&rdquo;</em>
        {" "}— it is <Strong>unscanned</Strong>. Nothing is failing. There is simply no control there.
      </P>

      <H2>Three things people assume cover this</H2>

      <H3>1. The endpoint AV licence</H3>

      <P>
        The organisation pays for endpoint protection across every laptop and server, so file
        scanning feels like a solved line item. It is solved for devices. It does nothing for a byte
        stream that enters through an HTTPS request and leaves into a bucket. Worse, the coverage
        number in the console still reads 100%, because every enrolled endpoint genuinely is
        protected — the upload path was never an endpoint to begin with.
      </P>

      <H3>2. The object store</H3>

      <P>
        Cloud storage is durable, encrypted, access-controlled, and versioned. None of those
        properties are detection. An encrypted, access-controlled, redundantly-replicated malicious
        document is still a malicious document, now stored more reliably than before. Some providers
        offer scanning as a separate paid feature; unless you switched it on, storing a file does not
        inspect it.
      </P>

      <H3>3. The file type allow-list</H3>

      <P>
        This one is worth being blunt about, because it is the most common false sense of security in
        upload code. A check like this:
      </P>

      <Pre label="what a content-type check actually sees">{`Content-Disposition: form-data; name="file"; filename="invoice.pdf"
Content-Type: application/pdf`}</Pre>

      <P>
        is reading a value <em>the client sent you</em>. The uploader chooses it. So does an attacker.
        Extension checks have the same problem, and magic-byte sniffing is better but still only tells
        you what a file claims to be structurally — a genuine PDF with a malicious payload is a
        genuine PDF.
      </P>

      <Note tone="warning" title="Including ours">
        geneav accepts an allow-list of declared content types, and{" "}
        <Strong>that allow-list is not a security control either</Strong>. It checks the
        client-declared multipart type, <Code>application/octet-stream</Code> is on the list, and a
        missing type skips the check entirely. It exists to reject obvious mistakes early, not to
        establish what a file is. The scan is what establishes that, because the engine reads the
        bytes regardless of what the envelope claimed.
      </Note>

      <H2>Where the scan actually belongs</H2>

      <P>
        The useful place to put a check is at ingestion — the moment the file crosses from
        &ldquo;something a stranger sent&rdquo; to &ldquo;something our system will store, process,
        or hand to another user.&rdquo; Concretely, before any of these:
      </P>

      <UL>
        <LI>the file becomes durable in your primary bucket;</LI>
        <LI>a worker opens it to extract text, thumbnails, or metadata;</LI>
        <LI>it is served back to a different user, which is where a stored file becomes a delivery
        mechanism;</LI>
        <LI>it is forwarded to a third party — a partner API, a support inbox, a printer.</LI>
      </UL>

      <P>
        A common and workable shape is a quarantine prefix: upload to a staging location, scan, then
        promote to the real bucket on a clean verdict and delete on an infected one. That keeps the
        scan off your request path when files are large, without letting an unscanned object sit
        somewhere the rest of the system treats as trusted.
      </P>

      <P>
        Whatever shape you choose, the scan wants to be an explicit, blocking decision with a
        recorded outcome. &ldquo;We will scan it later&rdquo; tends to mean the file is already in
        the trusted bucket and something has already read it.
      </P>

      <H2>What a scan at this layer can and cannot do</H2>

      <P>
        Being precise here matters, because overselling this control is how it ends up trusted for
        things it does not do.
      </P>

      <P>
        <Strong>It can</Strong> catch known malware — the very large body of already-catalogued
        viruses, trojans, worms, and malicious document constructions. In an upload pipeline that is
        most of what actually arrives, because most of what arrives is commodity, not bespoke.
      </P>

      <P>
        <Strong>It cannot</Strong> do several things people assume. Signature-based detection does
        not catch a genuinely novel or targeted payload — that is what the word{" "}
        <em>signature</em> means. It offers no behavioural analysis, no real-time protection (it runs
        when you call it, not continuously), and no remediation: a verdict tells you what a file is,
        and your code still has to decide what to do about it. It also does not reduce your endpoint
        antivirus footprint by a single seat. These are different controls on different surfaces.
      </P>

      <P>
        Treat it as one layer among several, sitting where you currently have none.
      </P>

      <H2>Build or buy is a real question</H2>

      <P>
        <A href="https://www.clamav.net/">ClamAV</A> is free, open source, and genuinely good at the
        job described above. Running it yourself is entirely reasonable, and if you have an existing
        deployment the integration cost is already sunk.
      </P>

      <P>
        What people underestimate is that the engine is the easy part. Around it you need an HTTP
        interface, authentication, quotas, rate limiting — <Code>clamd</Code> has no rate limiting
        whatsoever, so one caller can exhaust the box — TLS, deployment, and a signature-update
        pipeline whose reload transiently doubles a resident database already measured in gigabytes.
        That is the actual bill, and it is paid in engineering time rather than infrastructure.
      </P>

      <P>
        Either way, the decision worth making this week is the smaller one: whether anything is
        checking the files your application accepts. For most upload endpoints the honest answer is
        no, and nobody has explicitly decided that — it is just where the default lands.
      </P>
    </>
  );
}
