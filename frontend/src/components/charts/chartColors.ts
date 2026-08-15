/**
 * Chart color roles, mapped onto BankSphere's own brand tokens (not the
 * dataviz skill's generic 8-hue reference palette) and validated with
 * `validate_palette.js` as a 3-slot categorical set — worst adjacent CVD
 * ΔE 13.2 (protan), normal-vision ΔE 21.2, both clear of the 8/15
 * thresholds. `seriesGold` sits below 3:1 contrast on white (2.66:1),
 * which is why every chart using it ships visible direct labels or a
 * table view rather than relying on the fill color alone — see
 * docs/frontend/components.md#charts.
 */
export const CHART_COLORS = {
  /** Slot 1 — Deposits, the balance trend line. Brand primary. */
  seriesBlue: "#0B5FA3",
  /** Slot 2 — Withdrawals, and the second pie segment. Brand accent. */
  seriesGold: "#C9962B",
  /** Slot 3 — third pie segment onward. Brand secondary. */
  seriesTeal: "#0E9384",
  /** Hairline gridlines — one step off the white chart surface. */
  gridline: "#E2E8F0",
  /** Axis tick / muted label text. */
  axisText: "#94A3B8",
} as const;

export const PIE_SLOT_ORDER = [CHART_COLORS.seriesBlue, CHART_COLORS.seriesGold, CHART_COLORS.seriesTeal] as const;
