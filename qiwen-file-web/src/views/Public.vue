<template>
  <div class="file-wrapper" @dragenter="showUploadMask">
    <el-container class="el-container">
      <el-aside width="auto">
        <AsideMenuPub></AsideMenuPub>
      </el-aside>
      <el-container>
        <el-main class="el-main">
          <FileListPub></FileListPub>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import AsideMenuPub from '_c/public/AsideMenuPub.vue'
import FileListPub from '_c/public/FileListPub.vue'

export default {
  name: 'Public',
  components: {
    AsideMenuPub,
    FileListPub
  },
  methods: {
    // 显示拖拽上传文件遮罩
    showUploadMask(e) {
      e.stopPropagation();
      e.preventDefault();
      console.log("showUploadMask")
      this.$store.commit("showUploadMask");
    },
  },

}
</script>

<style lang="stylus" scoped>
@import '~_a/styles/varibles.styl';
@import '~_a/styles/mixins.styl';

.file-wrapper {
  width: 100% !important;
}

.el-container {
  .el-aside {
    height: calc(100vh - 61px);
    overflow: hidden;
  }
}

.el-main {
  padding: 0px 16px !important;
  overflow: hidden;
}
</style>
