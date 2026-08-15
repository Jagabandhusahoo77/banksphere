import { useState } from "react";
import { formatMoney } from "@/utils/format";
import { CHART_COLORS } from "./chartColors";

export interface BarChartGroup {
  label: string;
  deposits: number;
  withdrawals: number;
}

interface BarChartProps {
  data: BarChartGroup[];
  currency: string;
}

const WIDTH = 600;
const HEIGHT = 240;
const MARGIN = { top: 16, right: 16, bottom: 28, left: 56 };
const PLOT_WIDTH = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom;
const MAX_BAR_WIDTH = 24;
const BAR_GAP = 2;

function niceCeiling(value: number): number {
  if (value <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  const niceNormalized = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return niceNormalized * magnitude;
}

interface HoverTarget {
  groupIndex: number;
  series: "deposits" | "withdrawals";
}

/**
 * Grouped bar chart — Deposits vs Withdrawals per period. Two categorical
 * series (identity job, not magnitude), so both a legend and per-bar
 * hover/focus tooltips ship. `seriesGold` (Withdrawals) sits below 3:1
 * contrast on white — the visible value label at each bar's tip is the
 * required relief channel, not optional polish.
 */
export default function BarChart({ data, currency }: BarChartProps) {
  const [hover, setHover] = useState<HoverTarget | null>(null);

  if (data.length === 0) {
    return <p className="text-body-sm text-ink-muted py-10 text-center">Not enough transaction history yet.</p>;
  }

  const maxValue = niceCeiling(Math.max(...data.flatMap((d) => [d.deposits, d.withdrawals]), 1));
  const yTicks = 4;
  const groupWidth = PLOT_WIDTH / data.length;
  const barWidth = Math.min(MAX_BAR_WIDTH, (groupWidth - BAR_GAP - 12) / 2);

  const yFor = (value: number) => MARGIN.top + PLOT_HEIGHT - (value / maxValue) * PLOT_HEIGHT;
  const baselineY = MARGIN.top + PLOT_HEIGHT;

  const series: { key: "deposits" | "withdrawals"; color: string; label: string }[] = [
    { key: "deposits", color: CHART_COLORS.seriesBlue, label: "Deposits" },
    { key: "withdrawals", color: CHART_COLORS.seriesGold, label: "Withdrawals" },
  ];

  return (
    <div className="relative">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="w-full h-auto" role="img" aria-label="Deposits versus withdrawals by period">
        {Array.from({ length: yTicks + 1 }, (_, i) => {
          const value = (maxValue * i) / yTicks;
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

        {data.map((group, groupIndex) => {
          const groupCenter = MARGIN.left + groupWidth * groupIndex + groupWidth / 2;
          return (
            <g key={group.label}>
              <text x={groupCenter} y={HEIGHT - 8} textAnchor="middle" fontSize={10} fill={CHART_COLORS.axisText}>
                {group.label}
              </text>
              {series.map((s, sIndex) => {
                const value = group[s.key];
                const barHeight = (value / maxValue) * PLOT_HEIGHT;
                const x = groupCenter - barWidth - BAR_GAP / 2 + sIndex * (barWidth + BAR_GAP);
                const y = baselineY - barHeight;
                const isHovered = hover?.groupIndex === groupIndex && hover.series === s.key;
                const labelFits = barHeight > 16;
                return (
                  <g key={s.key}>
                    <rect
                      x={x}
                      y={y}
                      width={barWidth}
                      height={Math.max(barHeight, 1)}
                      rx={4}
                      fill={s.color}
                      opacity={isHovered ? 0.85 : 1}
                      onPointerEnter={() => setHover({ groupIndex, series: s.key })}
                      onPointerLeave={() => setHover(null)}
                      onFocus={() => setHover({ groupIndex, series: s.key })}
                      onBlur={() => setHover(null)}
                      tabIndex={0}
                      role="img"
                      aria-label={`${s.label}, ${group.label}: ${formatMoney(value, currency)}`}
                    />
                    {labelFits && value > 0 && (
                      <text x={x + barWidth / 2} y={y - 4} textAnchor="middle" fontSize={9} fill="#475569">
                        {formatMoney(value, currency).replace(/\.00$/, "")}
                      </text>
                    )}
                  </g>
                );
              })}
            </g>
          );
        })}

        <line x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={baselineY} y2={baselineY} stroke="#C3C2B7" strokeWidth={1} />
      </svg>

      {hover && (
        <div className="pointer-events-none absolute left-2 top-2 bg-ink-primary text-white text-caption rounded-md px-2.5 py-1.5 shadow-elevation-2">
          <p className="font-semibold">
            {formatMoney(data[hover.groupIndex][hover.series], currency)}
          </p>
          <p className="text-white/70">
            {series.find((s) => s.key === hover.series)?.label} · {data[hover.groupIndex].label}
          </p>
        </div>
      )}
    </div>
  );
}
