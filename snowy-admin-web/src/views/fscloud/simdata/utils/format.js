/**
 * simdata 模块公共格式化工具
 *
 * 与 TableTitleKeyMap.js 的列类型约定（s/t/i/f）配套：
 * - t（仿真毫秒时间戳）→ 人眼友好的 `d天 hh:mm:ss` / `hh:mm:ss` / `mm:ss` / `s`
 * - 表格（ResultTable.vue）与图表（ResultChart.vue）共用，避免两份实现漂移
 */

/**
 * 仿真毫秒 → 时间串
 * 天>0 → "X天 hh:mm:ss"；时>0 → "hh:mm:ss"；分>0 → "mm:ss"；秒>0 → "s"；否则 "0"
 */
export function fmtTimestamp(ms) {
	if (ms === null || ms === undefined) return '-'
	if (typeof ms === 'string' && ms.trim() === '') return '-'
	const totalSec = Math.floor(Number(ms) / 1000)
	if (isNaN(totalSec)) return '-'
	const d = Math.floor(totalSec / 86400)
	const h = Math.floor((totalSec % 86400) / 3600)
	const m = Math.floor((totalSec % 3600) / 60)
	const s = totalSec % 60
	if (d > 0) return `${d}天 ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
	if (h > 0) return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
	if (m > 0) return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
	if (s > 0) return `${s}`
	return '0'
}

/** 数值安全转换：空值/非数字 → null */
export function toNumber(value) {
	if (value === null || value === undefined || value === '') return null
	const n = Number(value)
	return isNaN(n) ? null : n
}
