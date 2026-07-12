import { baseRequest } from '@/utils/request'

const request = (url, ...arg) => baseRequest(`/fscloud/modelfile/` + url, ...arg)

/**
 * 仿真模型库Api接口管理器
 *
 * @author fengyuhe
 * @date  2026/06/04 21:42
 **/
export default {
	// 获取仿真模型库分页
	fsModelFilePage(data) {
		console.log('fsModelFilePage', data)
		return request('page', data, 'get')
	},
	// 提交仿真模型库表单 edit为true时为编辑，默认为新增
	fsModelFileSubmitForm(data, edit = false) {
		return request(edit ? 'edit' : 'add', data)
	},
	// 删除仿真模型库
	fsModelFileDelete(data) {
		return request('delete', data)
	},
	// 获取仿真模型库详情
	fsModelFileDetail(data) {
		return request('detail', data, 'get')
	},
	// 下载仿真模型库导入模板
    fsModelFileDownloadTemplate(data) {
		return request('downloadImportTemplate', data, 'get', {
			responseType: 'blob'
		})
    },
	// 导入仿真模型库
	fsModelFileImport(data) {
		return request('importData', data)
	},
	// 导出仿真模型库
	fsModelFileExport(data) {
		return request('exportData', data, 'post', {
			responseType: 'blob'
		})
	},
	// 根据ID列表批量查询记录（用于定时轮询刷新状态）
	fsModelFileListByIds(data) {
		return request('listByIds', data)
	},
}
