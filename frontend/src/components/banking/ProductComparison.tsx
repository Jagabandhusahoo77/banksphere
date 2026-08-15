interface ComparisonRow {
  feature: string;
  values: string[];
}

interface ProductComparisonProps {
  columns: string[];
  rows: ComparisonRow[];
}

/**
 * Renders a feature-comparison table on wider screens and the same data
 * as stacked per-product cards on mobile — both are in the DOM, toggled
 * with responsive display classes, so there's no JS breakpoint detection
 * (and nothing to get out of sync between the two renderings' data).
 */
export default function ProductComparison({ columns, rows }: ProductComparisonProps) {
  return (
    <div>
      {/* Desktop / tablet: table */}
      <div className="hidden sm:block table-scroll">
        <table className="w-full min-w-[480px] border-collapse">
          <thead>
            <tr className="border-b border-surface-border">
              <th className="text-left py-3 pr-4 text-label text-ink-muted">Feature</th>
              {columns.map((column) => (
                <th key={column} className="text-left py-3 px-4 text-label text-ink-primary font-semibold">
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.feature} className="border-b border-surface-border last:border-0">
                <td className="py-3 pr-4 text-body-sm font-medium text-ink-primary whitespace-nowrap">{row.feature}</td>
                {row.values.map((value, i) => (
                  <td key={columns[i]} className="py-3 px-4 text-body-sm text-ink-secondary">
                    {value}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Mobile: stacked cards, one per product */}
      <div className="sm:hidden space-y-4">
        {columns.map((column, columnIndex) => (
          <div key={column} className="bg-white rounded-lg border border-surface-border p-4">
            <p className="text-h3 text-ink-primary mb-3">{column}</p>
            <dl className="space-y-2">
              {rows.map((row) => (
                <div key={row.feature} className="flex items-start justify-between gap-4">
                  <dt className="text-caption text-ink-muted">{row.feature}</dt>
                  <dd className="text-body-sm text-ink-primary text-right">{row.values[columnIndex]}</dd>
                </div>
              ))}
            </dl>
          </div>
        ))}
      </div>
    </div>
  );
}
