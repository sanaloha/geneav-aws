import Link from "next/link";
import { REPO_URL } from "../site";

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
          <a href={REPO_URL} target="_blank" rel="noreferrer" className="nav-gh">
            ★ GitHub
          </a>
        </nav>
      </div>
    </header>
  );
}
