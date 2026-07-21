import clsx from "clsx";
import type { ReactNode } from "react";

type Props = {
  children: ReactNode;
  /** `hero` carries the extra top padding the landing header needs. */
  size?: "hero" | "default" | "tight";
  /** `subtle` and `dark` give alternating bands so long pages have rhythm. */
  tone?: "default" | "subtle" | "dark";
  className?: string;
  id?: string;
};

const sizes = {
  hero: "pt-20 pb-16 sm:pt-28 sm:pb-20",
  default: "py-14 sm:py-16",
  tight: "py-10",
};

const tones = {
  default: "bg-surface",
  subtle: "bg-surface-subtle",
  // Kept from the original dark identity; used for the closing CTA band.
  dark: "bg-[#0B1020] text-white",
};

/** Page section + centred content column. Replaces `.section` > `.container`. */
export default function Section({
  children,
  size = "default",
  tone = "default",
  className,
  id,
}: Props) {
  return (
    <section id={id} className={clsx(sizes[size], tones[tone], className)}>
      <div className="mx-auto w-full max-w-content px-5">{children}</div>
    </section>
  );
}
