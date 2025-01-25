<template>
	<div class="header-wrapper">
		<img class="logo" :src="logoUrl" @click="$router.push({ name: 'Home' })" />
		<img
			class="logo-xs"
			:src="logoUrlXs"
			@click="$router.push({ name: 'Home' })"
		/>

		<el-menu
			:default-active="activeIndex"
			class="el-menu-demo"
			mode="horizontal"
			@select="handleSelect"
		>
			<el-menu-item index="Home">首页</el-menu-item>
			<el-menu-item index="File">材料管理</el-menu-item>
			<!-- <el-menu-item index="File">个人网盘</el-menu-item> -->
			<el-menu-item index="4"
				><a href="https://pan.qiwenshare.com/docs/" target="_blank"
					>文档</a
				></el-menu-item
			>
		</el-menu>
		<el-menu
			:default-active="activeIndex"
			class="el-menu-demo"
			mode="horizontal"
			style="position: absolute; right: 0"
			@select="handleSelect"
		>
			<template>
				<el-menu-item class="login" index="Login" v-show="!isLogin"
					>登录</el-menu-item
				>
				<el-menu-item class="register" v-show="!isLogin" index="Register"
					>注册</el-menu-item
				>
			</template>
			<template>
				<!-- <div class="el-menu-item exit" @click="exitButton()">退出</div> -->
				<el-submenu class="user-exit-submenu" index="User" v-show="isLogin">
					<template slot="title">
						<i class="el-icon-user-solid"></i>
						<span>{{ username }}</span>
					</template>
					<el-menu-item>个人信息</el-menu-item>
					<el-menu-item @click="exitButton()">退出</el-menu-item>
				</el-submenu>
			</template>
		</el-menu>
		<div class="line"></div>
	</div>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
	name: 'Header',
	data() {
		return {
			logoUrl: require('_a/images/common/logo_header.png'),
			logoUrlXs: require('_a/images/common/logo_header_xs.png')
		}
	},
	computed: {
		...mapGetters(['isLogin', 'username']),
		// 当前激活菜单的 index
		activeIndex() {
			return this.$route.name || 'Home' //  获取当前路由名称
		},
		isProductEnv() {
			return (
				process.env.NODE_ENV !== 'development' &&
				location.origin === 'https://pan.qiwenshare.com'
			)
		},
		// 屏幕宽度
		screenWidth() {
			return this.$store.state.common.screenWidth
		}
	},
	methods: {
		// 奇文社区生产环境账户网址
		getAccountHref(path) {
			return `https://account.qiwenshare.com${path}?Rurl=${location.href}`
		},
		/**
		 * 退出登录
		 * @description 清除 cookie 存放的 token  并跳转到登录页面
		 */
		exitButton() {
			this.$message.success('退出登录成功！')
			this.$common.removeCookies(this.$config.tokenKeyName)
			this.$store.dispatch('getUserInfo').then(() => {
				this.$router.push({ name: 'Home' })
			})
		},

		handleSelect(key) {
			if (key === 'exit') {
				this.exitButton()
			} else if (key === 'Login') {
				if (this.isProductEnv) {
					location.href = this.getAccountHref('/login/account')
				} else {
					this.$router.push({ name: key })
				}
			} else if (key === 'Register') {
				if (this.isProductEnv) {
					location.href = this.getAccountHref('/register')
				} else {
					this.$router.push({ name: key })
				}
			} else if (key === 'File') {
				this.$router.push({ name: key, query: { fileType: 0, filePath: '/' } })
			} else {
				this.$router.push({ name: key })
			}
		}
	}
}
</script>

<style lang="stylus" scoped>
@import '~_a/styles/varibles.styl';

.header-wrapper {
  width: 100%;
  padding: 0 20px;
  box-shadow: $tabBoxShadow;
  display: flex;
  .logo {
    margin: 14px 24px 0 24px;
    display: inline-block;
    height: 40px;
    cursor: pointer;
  }
  .logo-xs {
    display: none;
  }
  >>> .el-menu--horizontal {
    .el-menu-item:not(.is-disabled):hover {
      border-bottom-color: $Primary !important;
      background: $tabBackColor;
    }
    .external-link {
      padding: 0;
      a {
        display: inline-block;
        padding: 0 20px;
      }
    }
  }
  .top-menu-list {
    flex: 1;
    .login, .register, .username, .exit, .user-exit-submenu {
      float: right;
    }
  }
}
</style>
