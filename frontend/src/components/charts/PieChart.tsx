import { useState } from "react";
import { formatMoney } from "@/utils/format";
import { PIE_SLOT_ORDER } from "./chartColors";

export interface PieChartSlice {
  label: string;
  value: number;
}

interface PieChartProps {
  data: PieChartSlice[];
  currency: string;
}

const SIZE = 200;
const CENTER = SIZE / 2;
const RADIUS = SIZE / 2 - 4;
const GAP_STROKE_WIDTH = 2;

function polarToCartesian(angle: number) {
  return { x: CENTER + RADIUS * Math.cos(angle), y: CENTER + RADIUS * Math.sin(angle) };
}

function arcPath(startAngle: number, endAngle: number) {
  const start = polarToCartesian(endAngle);
  const end = polarToCartesian(startAngle);
  const largeArcFlag = endAngle - startAngle <= Math.PI ? 0 : 1;
  return `M ${CENTER} ${CENTER} L ${start.x} ${start.y} A ${RADIUS} ${RADIUS} 0 ${largeArcFlag} 0 ${end.x} ${end.y} Z`;
}

/**
 * Part-to-whole pie — categorical color, ≤6 segments. Deliberately renders
 * nothing below 3 segments: a 1-slice pie is just the total (a stat tile's
 * job) and a 2-slice pie is the documented anti-pattern (a bar, or the two
 * numbers, reads better) — see references/anti-patterns.md in the dataviz
 * skill. The caller falls back to its existing stat-tile balance cards in
 * that case rather than rendering a degenerate pie.
 */
export default function PieChart({ data, currency }: PieChartProps) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  if (data.length < 3) return null;

  const total = data.reduce((sum, d) => sum + d.value, 0);
  if (total <= 0) return null;

  let cumulativeAngle = -Math.PI / 2;
  const slices = data.map((d, i) => {
    const share = d.value / total;
    const startAngle = cumulativeAngle;
    const endAngle = cumulativeAngle + share * 2 * Math.PI;
    cumulativeAngle = endAngle;
    return { ...d, share, startAngle, endAngle, color: PIE_SLOT_ORDER[i % PIE_SLOT_ORDER.length] };
  });

  return (
    <div className="flex flex-col sm:flex-row items-center gap-6">
      <svg viewBox={`0 0 ${SIZE} ${SIZE}`} className="w-44 h-44 shrink-0" role="img" aria-label="Balance distribution across accounts">
        {slices.map((slice, i) => (
          <path
            key={slice.label}
            d={arcPath(slice.startAngle, slice.endAngle)}
            fill={slice.color}
            stroke="#FFFFFF"
            strokeWidth={GAP_STROKE_WIDTH}
            opacity={hoverIndex === null || hoverIndex === i ? 1 : 0.55}
            onPointerEnter={() => setHoverIndex(i)}
            onPointerLeave={() => setHoverIndex(null)}
            onFocus={() => setHoverIndex(i)}
            onBlur={() => setHoverIndex(null)}
            tabIndex={0}
            role="img"
            aria-label={`${slice.label}: ${formatMoney(slice.value, currency)}, ${Math.round(slice.share * 100)}%`}
          />
        ))}
        {slices.map(
          (slice, i) =>
            slice.share >= 0.1 && (
              <text
                key={`${slice.label}-label`}
                x={CENTER + (RADIUS * 0.65) * Math.cos((slice.startAngle + slice.endAngle) / 2)}
                y={CENTER + (RADIUS * 0.65) * Math.sin((slice.startAngle + slice.endAngle) / 2)}
                textAnchor="middle"
                dominantBaseline="middle"
                fontSize={11}
                fontWeight={600}
                fill="#FFFFFF"
                className={hoverIndex !== null && hoverIndex !== i ? "opacity-55" : ""}
              >
                {Math.round(slice.share * 100)}%
              </text>
            ),
        )}
      </svg>

      <ul className="flex-1 w-full space-y-2">
        {slices.map((slice, i) => (
          <li
            key={slice.label}
            className={`flex items-center justify-between gap-3 text-body-sm rounded-md px-1.5 py-1 -mx-1.5 transition-opacity ${
              hoverIndex !== null && hoverIndex !== i ? "opacity-55" : ""
            }`}
            onPointerEnter={() => setHoverIndex(i)}
            onPointerLeave={() => setHoverIndex(null)}
          >
            <span className="flex items-center gap-2 text-ink-secondary">
              <span className="w-3 h-3 rounded-sm shrink-0" style={{ backgroundColor: slice.color }} aria-hidden="true" />
              {slice.label}
            </span>
            <span className="font-medium text-ink-primary">{formatMoney(slice.value, currency)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
