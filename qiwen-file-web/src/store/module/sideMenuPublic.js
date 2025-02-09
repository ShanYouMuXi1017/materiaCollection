import { getStorage } from '_r/public.js';

export default {
	state: {
		used: 0, // 文件已占用的存储空间大小
		total: 0, // 总存储空间大小
	},
	mutations: {
		/**
		 * 保存文件已占用的存储空间大小
		 * @param {object} state Vuex 的 state 对象
		 * @param {number} data 存储大小
		 */
		setUsed(state, data) {
			state.used = data;
		},
		setTotal(state, data) {
			state.total = data;
		},
	},
	actions: {
		/**
		 * 获取文件已占用的存储空间
		 */
		async showStorage(context) {
			try {
				const res = await getStorage();
				if (res.success) {
					// 提取存储空间数据
					const { usedStorageSize = 0, totalStorageSize = 0 } = res.data || {};
					const used = Number(usedStorageSize);
					const total = Number(totalStorageSize);

					// 提交 mutations 更新状态
					context.commit('setUsed', used);
					context.commit('setTotal', total);
				} else {
					// 如果请求失败，显示错误信息
					context.rootState.$message.error(res.message);
				}
			} catch (error) {
				// 捕获网络或其他错误
				console.error('Error fetching storage data:', error);
				context.rootState.$message.error('Failed to fetch storage data.');
			}
		}
	}
};
