<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
<!--      <el-form-item label="店铺名" prop="name">-->
<!--        <el-input-->
<!--          v-model="queryParams.name"-->
<!--          placeholder="请输入店铺名"-->
<!--          clearable-->
<!--          @keyup.enter.native="handleQuery"-->
<!--        />-->
<!--      </el-form-item>-->
      <el-form-item label="平台" prop="type">
        <el-select v-model="queryParams.type" placeholder="请选择平台" @change="handleQuery">
          <el-option
            v-for="item in typeList"
            :key="item.id"
            :label="item.name"
            :value="item.id">
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['shop:shop:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handlePull"
        >API拉取快递公司数据</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="dataList" >
<!--      <el-table-column type="selection" width="55" align="center" />-->
      <el-table-column label="id" align="center" prop="id" />
      <el-table-column label="快递公司" align="center" prop="name" />
       <el-table-column label="编码" align="center" prop="code" />
       <el-table-column label="备注" align="center" prop="remark" />
       <el-table-column label="状态" align="center" prop="status" >
         <template slot-scope="scope">
           <el-tag size="small" v-if="scope.row.status === 0 || !scope.row.status"> 未启用</el-tag>
           <el-tag size="small" v-if="scope.row.status === 1"> 启用</el-tag>
         </template>
       </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            v-if="scope.row.status === 0 || !scope.row.status"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdateStatus(scope.row)"
          >开启</el-button>
          <el-button
            size="mini"
            v-if="scope.row.status === 1"
            type="text"
            icon="el-icon-delete"
            @click="handleUpdateStatus(scope.row)"
          >关闭</el-button>
          <el-row>
            <el-button
              size="mini"
              type="text"
              icon="el-icon-edit"
              @click="handleUpdate(scope.row)"
              v-hasPermi="['shop:shop:edit']"
            >修改</el-button>
            <el-button
              size="mini"
              type="text"
              icon="el-icon-delete"
              @click="handleDelete(scope.row)"
              v-hasPermi="['shop:shop:remove']"
            >删除</el-button>
          </el-row>
        </template>
      </el-table-column>

    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
    <!-- 添加或修改店铺对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="快递公司名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入快递公司名称" />
        </el-form-item>
        <el-form-item label="快递公司编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入快递公司编码" />
        </el-form-item>
        <el-form-item label="平台" prop="platformId">
        <el-select v-model="form.platformId" placeholder="请选择平台" clearable >
          <el-option
            v-for="item in typeList"
            :key="item.id"
            :label="item.name"
            :value="item.id">
          </el-option>
        </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="remark">
          <el-input type="textarea" v-model="form.remark" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态">
            <el-option label="启用" value="1"></el-option>
            <el-option label="禁用" value="0"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listLogistics,
  updateStatus,
  addLogistics,
  getLogistics,
  updateLogistics,
  delLogistics,
  listPlatform
} from "@/api/shop/shop";
import {MessageBox} from "element-ui";
import {isRelogin} from "@/utils/request";
import {pullLogisticsTao,pullLogisticsJd} from "@/api/tao/shop_api";

export default {
  name: "Shop",
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 店铺表格数据
      dataList:[],
      typeList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      apiOpen: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        id:null,
        shopId:null
      },
      // 表单参数
      form: {
        platformId:null,
        shopId:0,
        logisticsId:0,
        status:'1'
      },
      // 表单校验
      rules: {
        name: [{ required: true, message: "不能为空", trigger: "blur" }],
        number: [{ required: true, message: "不能为空", trigger: "blur" }],
        platformId: [{ required: true, message: "不能为空", trigger: "blur" }],
        status: [{ required: true, message: "不能为空", trigger: "blur" }],
      }
    };
  },
  created() {

  },
  mounted() {

    listPlatform({status:0}).then(res => {
      this.typeList = res.rows;
      if(this.$route.query.id){
        this.queryParams.type = this.$route.query.id+""
      }else{
        this.queryParams.type = this.typeList[0].id
      }
      if(this.$route.query.shopId){
        this.queryParams.shopId = this.$route.query.shopId+""
      }
      this.getList();
    })

  },
  methods: {
    /** 查询店铺列表 */
    getList() {
      this.loading = true;

      listLogistics(this.queryParams).then(response => {
        this.dataList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },

    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handlePull() {
      if(this.queryParams.type) {
        console.log('=====拉取快递公司=====', this.queryParams.type)
        // if (this.queryParams.type === 100) {
        //   pullLogisticsTao({}).then(response => {
        //     console.log('拉取TAO接口返回=====', response)
        //     if (response.code === 1401) {
        //       MessageBox.confirm('Token已过期，需要重新授权', '系统提示', {
        //         confirmButtonText: '重新授权',
        //         cancelButtonText: '取消',
        //         type: 'warning'
        //       }).then(() => {
        //         isRelogin.show = false;
        //         // store.dispatch('LogOut').then(() => {
        //         location.href = response.data.tokenRequestUrl + '?shopId=' + this.queryParams.shopId
        //         // })
        //       }).catch(() => {
        //         isRelogin.show = false;
        //       });
        //
        //       // return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
        //     } else{
        //       this.$modal.msgSuccess(JSON.stringify(response));
        //       this.getList()
        //     }
        //
        //   })
        // } else if (this.queryParams.type === 200 || this.queryParams.type===280) {
        //   console.log('=====aaaaa=====')
        //   //jd
        //   pullLogisticsJd({shopId: this.$route.query.shopId}).then(response => {
        //     console.log('拉取JD接口返回=====', response)
        //     if (response.code === 1401) {
        //       MessageBox.confirm('Token已过期，需要重新授权', '系统提示', {
        //         confirmButtonText: '重新授权',
        //         cancelButtonText: '取消',
        //         type: 'warning'
        //       }).then(() => {
        //         isRelogin.show = false;
        //         // store.dispatch('LogOut').then(() => {
        //         location.href = response.data.tokenRequestUrl + '?shopId=' + this.queryParams.shopId
        //         // })
        //       }).catch(() => {
        //         isRelogin.show = false;
        //       });
        //
        //       // return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
        //     } else{
        //       this.$modal.msgSuccess(JSON.stringify(response));
        //       this.getList()
        //     }
        //
        //   })
        // }else if(this.queryParams.type=999){
        //   this.$modal.msgSuccess("线下渠道请手动添加");
        // }else{
        //   this.$modal.msgSuccess("还未实现");
        // }
        this.$modal.msgSuccess("开源版本不支持拉取！请手动添加");
      }else{
        this.$modal.msgWarning("请先选择平台");
      }

      // this.$modal.msgSuccess("请先配置API");
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除快递公司为"' + ids + '"的数据项？').then(function() {
        return delLogistics(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    handleUpdateStatus(row){
      updateStatus({id:row.id,status:row.status}).then(response => {
        console.log('更新状态====',response)
        this.getList()
      })
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        name: null,
        number: null,
        remark: null,
        status: null,
      };
      this.resetForm("form");
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加快递公司";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getLogistics(id).then(response => {
        this.form = response.data;
        this.form.status = response.data.status+'';
        this.open = true;
        this.title = "修改";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id != null) {
            updateLogistics(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addLogistics(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    }
  }
};
</script>
