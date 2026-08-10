import { baseRequest } from '@/utils/request'

const request = (url, ...arg) => baseRequest(`/fscloud/sim/` + url, ...arg)

/**
 * 仿真模型库Api接口管理器
 *
 * @author fengyuhe
 * @date  2026/06/04 21:42
 **/
export default {
	// 加载仿真模型
	fsSimLoadModel(data) {
		return request('loadModel', data, 'post')
	},
	// 启动仿真
	fsSimRunModel(data) {
		return request('runModel', data, 'post')
	},
	// 停止仿真
	fsSimStopModel(data) {
		return request('stopModel', data, 'post')
	},
	// 暂停仿真
	fsSimPauseModel(data) {
		return request('pauseModel', data, 'post')
	},
	// 继续仿真
	fsSimResumeModel(data) {
		return request('resumeModel', data, 'post')
	},
	// 重置仿真
	fsSimResetModel(data) {
		return request('resetModel', data, 'post')
	},
	// 卸载模型
	fsSimUnloadModel(data) {
		return request('unloadModel', data, 'post')
	},
	// 查询当前用户已加载的模型
	fsSimGetLoadedModel() {
		return request('getLoadedModel', {}, 'post')
	},
	// 保存参数快照
	fsSimSaveSnapshot(data) {
		return request('saveSnapshot', data, 'post')
	},
	// 查询某模型的所有快照列表
	fsSimListSnapshots(data) {
		return request('listSnapshots', data, 'post')
	},
	// 查询单个快照详情
	fsSimGetSnapshot(data) {
		return request('getSnapshot', data, 'post')
	},
	// 删除参数快照
	fsSimDeleteSnapshot(data) {
		return request('deleteSnapshot', data, 'post')
	},
	// 获取动画层列表
	fsSimListAnimationLayers(data) {
		return request('listAnimationLayers', data, 'post')
	},
	// 选择动画层
	fsSimSelectAnimationLayer(data) {
		return request('selectAnimationLayer', data, 'post')
	},
	// 开关动画
	fsSimSetAnimationEnabled(data) {
		return request('setAnimationEnabled', data, 'post')
	},
	// 设置仿真加速因子
	fsSimSetAccelerateFactor(data) {
		return request('setAccelerateFactor', data, 'post')
	},
	// 获取 SVG 高清静态图
	fsSimGetSvgPicture(data) {
		return request('getSvgPicture', data, 'post')
	},
	// 获取仿真模型的参数表列表
	fsSimListParameterTables(data) {
		return request('listParameterTables', data, 'post')
	},
	// 获取参数快照
	fsSimGetParameterSnapshot(data) {
		return request('getParameterSnapshot', data, 'post')
	},
	// 保存参数快照
	fsSimSaveParameterSnapshot(data) {
		return request('saveParameterSnapshot', data, 'post')
	},
	// 参数快照写模型（调用 MCP 工具写入仿真模型）
	fsSaveParamSnapshotToModel(data) {
		return request('saveParamSnapshotToModel', data, 'post')
	},
	// 获取仿真结果快照
	fsSimGetResultSnapshot(data) {
		return request('getResultSnapshot', data, 'post')
	},
	// 获取结果数据集分页
	fsSimGetResultDatasetPage(data) {
		return request('getResultDatasetPage', data, 'post')
	},
	// 保存结果快照（成对落库：参数+结果）
	fsSimSaveResultSnapshot(data) {
		return request('saveResultSnapshot', data, 'post')
	},
	// 查询某模型的所有结果快照列表
	fsSimListResultSnapshots(data) {
		return request('listResultSnapshots', data, 'post')
	},
	// 查询单个结果快照详情
	fsSimGetResultSnapshotById(data) {
		return request('getResultSnapshotById', data, 'post')
	},
	// 按页读取结果快照某数据集数据（数据库）
	fsSimGetResultDatasetPageInDb(data) {
		return request('getResultDatasetPageInDb', data, 'post')
	},
	// 删除结果快照（连带成对参数快照与 partCycleTime 文件）
	fsSimDeleteResultSnapshot(data) {
		return request('deleteResultSnapshot', data, 'post')
	}
}
