import { useRef, useState } from "react";
import { formatMoney } from "@/utils/format";
import { CHART_COLORS } from "./chartColors";

export interface LineChartPoint {
  /** Display label for the axis/tooltip (e.g. "12 Aug"). */
  label: string;
  value: number;
}

interface LineChartProps {
  data: LineChartPoint[];
  currency: string;
}

const WIDTH = 600;
const HEIGHT = 240;
const MARGIN = { top: 16, right: 16, bottom: 28, left: 56 };
const PLOT_WIDTH = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom;

/** Rounds a max value up to a "clean" tick ceiling (nearest 1/2/5 * 10^n). */
function niceCeiling(value: number): number {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const niceNormalized = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return niceNormalized * magnitude;
}

/**
 * Single-series line chart — balance trend. One hue (sequential job, not
 * categorical), so no legend box: the card title already says what's
 * plotted. Crosshair + tooltip on hover/focus; every value is also in the
 * table-view twin, so the tooltip only enhances, never gates.
 */
export default function LineChart({ data, currency }: LineChartProps) {
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  if (data.length === 0) {
    return <p className="text-body-sm text-ink-muted py-10 text-center">Not enough transaction history yet.</p>;
  }

  const values = data.map((d) => d.value);
  const minValue = Math.min(0, ...values);
  const maxValue = niceCeiling(Math.max(...values, 1));
  const yTicks = 4;

  const xFor = (index: number) =>
    data.length === 1 ? MARGIN.left + PLOT_WIDTH / 2 : MARGIN.left + (index / (data.length - 1)) * PLOT_WIDTH;
  const yFor = (value: number) =>
    MARGIN.top + PLOT_HEIGHT - ((value - minValue) / (maxValue - minValue || 1)) * PLOT_HEIGHT;

  const linePath = data.map((d, i) => `${i === 0 ? "M" : "L"}${xFor(i)},${yFor(d.value)}`).join(" ");
  const areaPath = `${linePath} L${xFor(data.length - 1)},${yFor(minValue)} L${xFor(0)},${yFor(minValue)} Z`;

  const handlePointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    const svg = svgRef.current;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const relativeX = ((event.clientX - rect.left) / rect.width) * WIDTH;
    const clampedX = Math.min(Math.max(relativeX, MARGIN.left), WIDTH - MARGIN.right);
    const ratio = data.length === 1 ? 0 : (clampedX - MARGIN.left) / PLOT_WIDTH;
    const nearestIndex = Math.round(ratio * (data.length - 1));
    setHoverIndex(Math.min(Math.max(nearestIndex, 0), data.length - 1));
  };

  const hovered = hoverIndex !== null ? data[hoverIndex] : null;
  const last = data[data.length - 1];

  return (
    <div className="relative">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="w-full h-auto"
        role="img"
        aria-label={`Balance trend from ${data[0].label} to ${last.label}, ending at ${formatMoney(last.value, currency)}`}
        onPointerMove={handlePointerMove}
        onPointerLeave={() => setHoverIndex(null)}
      >
        {/* Gridlines */}
        {Array.from({ length: yTicks + 1 }, (_, i) => {
          const value = minValue + ((maxValue - minValue) * i) / yTicks;
          const y = yFor(value);
          return (
            <g key={i}>
              <line x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={y} y2={y} stroke={CHART_COLORS.gridline} strokeWidth={1} />
              <text x={MARGIN.left - 8} y={y} textAnchor="end" dominantBaseline="middle" fontSize={10} fill={CHART_COLORS.axisText}>
                {formatMoney(value, currency).replace(/\.00$/, "")}
              </text>
            </g>
          );
        })}

        {/* X-axis labels — first, last, and hovered (avoid clutter) */}
        {[0, data.length - 1].map((i) => (
          <text key={i} x={xFor(i)} y={HEIGHT - 8} textAnchor={i === 0 ? "start" : "end"} fontSize={10} fill={CHART_COLORS.axisText}>
            {data[i].label}
          </text>
        ))}

        {/* Area wash */}
        <path d={areaPath} fill={CHART_COLORS.seriesBlue} opacity={0.1} stroke="none" />

        {/* Line */}
        <path d={linePath} fill="none" stroke={CHART_COLORS.seriesBlue} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />

        {/* End marker + direct label */}
        <circle cx={xFor(data.length - 1)} cy={yFor(last.value)} r={4} fill={CHART_COLORS.seriesBlue} stroke="#FFFFFF" strokeWidth={2} />
        <text
          x={xFor(data.length - 1)}
          y={yFor(last.value) - 10}
          textAnchor="end"
          fontSize={11}
          fontWeight={600}
          fill="#0F172A"
        >
          {formatMoney(last.value, currency)}
        </text>

        {/* Crosshair */}
        {hovered && hoverIndex !== null && (
          <>
            <line
              x1={xFor(hoverIndex)}
              x2={xFor(hoverIndex)}
              y1={MARGIN.top}
              y2={MARGIN.top + PLOT_HEIGHT}
              stroke={CHART_COLORS.axisText}
              strokeWidth={1}
            />
            <circle cx={xFor(hoverIndex)} cy={yFor(hovered.value)} r={5} fill={CHART_COLORS.seriesBlue} stroke="#FFFFFF" strokeWidth={2} />
          </>
        )}

        {/* Full-width hit layer for pointer tracking */}
        <rect x={MARGIN.left} y={MARGIN.top} width={PLOT_WIDTH} height={PLOT_HEIGHT} fill="transparent" />
      </svg>

      {hovered && hoverIndex !== null && (
        <div
          className="absolute pointer-events-none bg-ink-primary text-white text-caption rounded-md px-2.5 py-1.5 shadow-elevation-2 -translate-x-1/2 -translate-y-full"
          style={{ left: `${(xFor(hoverIndex) / WIDTH) * 100}%`, top: `${(yFor(hovered.value) / HEIGHT) * 100 - 4}%` }}
        >
          <p className="font-semibold">{formatMoney(hovered.value, currency)}</p>
          <p className="text-white/70">{hovered.label}</p>
        </div>
      )}
    </div>
  );
}
