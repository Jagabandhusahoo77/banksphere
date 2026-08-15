import { useState, type ReactNode } from "react";

interface ChartCardProps {
  title: string;
  subtitle?: string;
  /** Rendered between the header and the chart/table body — legend swatches for 2+ series. */
  legend?: ReactNode;
  chart: ReactNode;
  /** The WCAG-clean equivalent of the chart — every value the chart shows, reachable without hovering. */
  table: ReactNode;
}

/**
 * Shared chrome for every dashboard chart: title, optional legend, and a
 * table-view toggle (the accessibility twin every chart needs — see
 * docs/frontend/components.md#charts). Individual charts only implement
 * the SVG mark and the <table> body; this owns the card, the toggle
 * state, and keeps that toggle in the same place on every chart.
 */
export default function ChartCard({ title, subtitle, legend, chart, table }: ChartCardProps) {
  const [showTable, setShowTable] = useState(false);

  return (
    <div className="bg-white rounded-lg border border-surface-border p-5 sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-h3 text-ink-primary">{title}</h3>
          {subtitle && <p className="mt-0.5 text-body-sm text-ink-secondary">{subtitle}</p>}
        </div>
        <button
          type="button"
          onClick={() => setShowTable((current) => !current)}
          className="shrink-0 text-caption font-medium text-brand-primary hover:underline"
        >
          {showTable ? "View chart" : "View as table"}
        </button>
      </div>
      {legend && !showTable && <div className="flex flex-wrap items-center gap-4 mt-4">{legend}</div>}
      <div className="mt-5">{showTable ? table : chart}</div>
    </div>
  );
}
