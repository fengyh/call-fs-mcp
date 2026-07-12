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
}
