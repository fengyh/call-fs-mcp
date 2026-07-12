<template>
	<xn-form-container
		:title="formData.id ? '编辑仿真模型库' : '增加仿真模型库'"
		:width="700"
		v-model:open="open"
		:destroy-on-close="true"
		@close="onClose"
	>
		<a-form ref="formRef" :model="formData" :rules="formRules" layout="vertical">
			<a-form-item label="仿真模型名称：" name="name">
				<a-input v-model:value="formData.name" placeholder="请输入仿真模型名称" allow-clear />
			</a-form-item>
			<a-form-item label="模型描述：" name="description">
				<a-input v-model:value="formData.description" placeholder="请输入模型描述" allow-clear />
			</a-form-item>
			<a-form-item label="引擎版本：" name="engineVersion">
				<a-input v-model:value="formData.engineVersion" placeholder="请输入引擎版本" allow-clear />
			</a-form-item>
			<a-form-item label="模型用途：" name="modelUsage">
				<a-input v-model:value="formData.modelUsage" placeholder="请输入模型用途" allow-clear />
			</a-form-item>
			<a-form-item label="创建人ID：" name="userId">
				<a-input v-model:value="formData.userId" placeholder="请输入创建人ID" allow-clear />
			</a-form-item>
			<a-form-item label="创建人名称：" name="userName">
				<a-input v-model:value="formData.userName" placeholder="请输入创建人名称" allow-clear />
			</a-form-item>
			<a-form-item label="模型所属公司：" name="modelCompany">
				<a-input v-model:value="formData.modelCompany" placeholder="请输入模型所属公司" allow-clear />
			</a-form-item>
			<a-form-item label="所属工厂：" name="modelFactory">
				<a-input v-model:value="formData.modelFactory" placeholder="请输入所属工厂" allow-clear />
			</a-form-item>
			<a-form-item label="所属仓库：" name="modelWarehouse">
				<a-input v-model:value="formData.modelWarehouse" placeholder="请输入所属仓库" allow-clear />
			</a-form-item>
			<a-form-item label="模型版本：" name="modelVersion">
				<a-input v-model:value="formData.modelVersion" placeholder="请输入模型版本" allow-clear />
			</a-form-item>
			<a-form-item label="分享标识：0-不分享，1-分享：" name="shareFlag">
				<a-input v-model:value="formData.shareFlag" placeholder="请输入分享标识：0-不分享，1-分享" allow-clear />
			</a-form-item>
		</a-form>
		<template #footer>
			<a-button style="margin-right: 8px" @click="onClose">关闭</a-button>
			<a-button type="primary" @click="onSubmit" :loading="submitLoading">保存</a-button>
		</template>
	</xn-form-container>
</template>

<script setup name="fsModelFileForm">
	import { cloneDeep } from 'lodash-es'
	import { required } from '@/utils/formRules'
	import fsModelFileApi from '@/api/fscloud/fsModelFileApi'
	// 抽屉状态
	const open = ref(false)
	const emit = defineEmits({ successful: null })
	const formRef = ref()
	// 表单数据
	const formData = ref({})
	const submitLoading = ref(false)

	// 打开抽屉
	const onOpen = (record) => {
		open.value = true
		if (record) {
			let recordData = cloneDeep(record)
			formData.value = Object.assign({}, recordData)
		}
	}
	// 关闭抽屉
	const onClose = () => {
		formRef.value.resetFields()
		formData.value = {}
		open.value = false
	}
	// 默认要校验的
	const formRules = {
	}
	// 验证并提交数据
	const onSubmit = () => {
		formRef.value
			.validate()
			.then(() => {
				submitLoading.value = true
				const formDataParam = cloneDeep(formData.value)
				fsModelFileApi
					.fsModelFileSubmitForm(formDataParam, formDataParam.id)
					.then(() => {
						onClose()
						emit('successful')
					})
					.finally(() => {
						submitLoading.value = false
					})
			})
			.catch(() => {})
	}
	// 抛出函数
	defineExpose({
		onOpen
	})
</script>
