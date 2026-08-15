import { useEffect, useRef, useState, type RefObject } from "react";

/**
 * Latches `isVisible` to `true` the first time the ref'd element scrolls
 * into view, and never re-triggers — used to drive a one-time fade/slide-in
 * on homepage sections rather than a repeating scroll animation.
 *
 * Respects `prefers-reduced-motion`: checked *inside* the hook, not left to
 * a CSS media query alone, because the trigger itself (a caller typically
 * starts the element at `opacity-0` until `isVisible` flips) must never
 * fire in the first place for a reduced-motion user — gating only via CSS
 * would risk content staying invisible if the intersection observer never
 * fires the same way. When reduced motion is requested, this returns
 * `isVisible: true` immediately and never creates an observer.
 */
export function useInViewport<T extends HTMLElement>(
  options?: IntersectionObserverInit,
): { ref: RefObject<T>; isVisible: boolean } {
  const ref = useRef<T>(null);
  const prefersReducedMotion =
    typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const [isVisible, setIsVisible] = useState(prefersReducedMotion);

  useEffect(() => {
    if (prefersReducedMotion) return;

    const element = ref.current;
    if (!element) return;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setIsVisible(true);
        observer.disconnect();
      }
    }, options);

    observer.observe(element);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { ref, isVisible };
}
