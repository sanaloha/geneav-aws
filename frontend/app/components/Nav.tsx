import Link from "next/link";

export default function Nav() {
  return (
    <header className="nav">
      <div className="container nav-inner">
        <Link href="/" className="brand">
          gene<span>av</span>
        </Link>
        <nav className="nav-links">
          <Link href="/features">Features</Link>
          <Link href="/developers">Developers</Link>
          <Link href="/about">About</Link>
        </nav>
      </div>
    </header>
  );
}
