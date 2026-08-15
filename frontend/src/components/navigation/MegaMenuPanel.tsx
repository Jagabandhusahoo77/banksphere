import { Link } from "react-router-dom";
import type { NavColumn } from "@/data/navigation";
import Icon from "@/components/common/Icon";

interface MegaMenuPanelProps {
  columns: NavColumn[];
  onNavigate: () => void;
}

/**
 * Full-width dropdown positioned by its parent (PublicHeader, which
 * establishes the positioning context — see that file's header comment on
 * why `position: sticky` there still stays in normal document flow).
 * Deliberately not a per-trigger-width popover: a fixed `grid-cols-*`
 * class (never a dynamic `grid-cols-${n}` string — see
 * ProductDetailLayout.tsx's KEY_FACT_GRID_CLASSES for why that pattern is
 * required) works for both Personal's 5-column menu and Business/NRI/
 * Premium Banking's single-column ones without any per-menu sizing logic.
 */
export default function MegaMenuPanel({ columns, onNavigate }: MegaMenuPanelProps) {
  return (
    <div className="absolute left-0 right-0 top-full z-40 border-t border-surface-border bg-white shadow-elevation-3">
      <div className="mx-auto max-w-content px-4 sm:px-6 py-8 grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-8">
        {columns.map((column) => (
          <div key={column.heading}>
            <p className="text-label text-ink-muted uppercase tracking-wide mb-3">{column.heading}</p>
            <ul className="space-y-1">
              {column.items.map((item) => (
                <li key={item.label}>
                  <Link
                    to={item.to}
                    onClick={onNavigate}
                    className="flex items-center gap-2.5 rounded-md px-2 py-2 -mx-2 text-body-sm text-ink-secondary transition-colors hover:bg-surface-muted hover:text-brand-primary"
                  >
                    {item.icon && <Icon name={item.icon} size={16} className="text-brand-primary shrink-0" />}
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
}
