<template>
  <div class="workorder_head_container card" style="padding: 0;">
    <div class="workorder_head_left" :style="{ backgroundColor: statusColor }">
      <i class="img_icon" style="width: 70%;" :style="{ backgroundImage: `url(${headIcon})` }"></i>
      <div style="color: #fff;">{{ headTitle }}</div>
    </div>
    <div class="workorder_head_right">
      <div class="workorder_head_right_item" style="justify-content: left; font-size: 20px;">
        <div>
          <span style="font-weight: 700;">{{ `${oriInfo.createUserName || ''} ` }}</span>
          <span>{{ oriInfo.createUserPhone || '' }}</span>
        </div>
      </div>
      <div class="workorder_head_right_item">
        <div class="head_align_item">
          <span>提交店铺:</span>
          <span class="form_text">{{ `${oriInfo.createMerchantName || ''} / ${oriInfo.createMerchantCode || ''}` }}</span>
        </div>
        <div class="head_align_item">
          <span>提交人:</span>
          <span class="form_text">{{ oriInfo.createUserName || '' }}</span>
        </div>
        <div class="head_align_item">
          <span>处理人:</span>
          <span class="form_text">{{ oriInfo.handleUserName || '' }}</span>
        </div>
        <div class="head_align_item">
          <span>保险公司:</span>
          <span class="form_text">
            {{ (oriInfo.insuranceMerchantName || '') + ' / ' + (oriInfo.insuranceMerchantCode || '') }}
          </span>
        </div>
      </div>
      <div class="workorder_head_right_item">
        <div class="head_align_item">
          <span>工单号:</span>
          <span class="form_text">{{ oriInfo.code || '' }}</span>
        </div>
        <div class="head_align_item">
          <span>工单状态:</span>
          <span class="form_text">{{ headTitle || '' }}</span>
        </div>
        <div class="head_align_item">
          <span>创建时间:</span>
          <span class="form_text">{{ formatSecondTimestamp(oriInfo.createTime) || '' }}</span>
        </div>
        <div class="head_align_item">
          <span>跟进时间:</span>
          <span class="form_text">{{ formatSecondTimestamp(oriInfo.updateTime) || '' }}</span>
        </div>
      </div>
    </div>
  </div>
  <div class="container_body card" style="padding: 10px 20px">
    <!-- 步骤进度 -->
    <div class="step_container">
      <el-steps style="min-width: 600px" :active="stepStatus.nowStep" align-center finish-status="success">
        <el-step title="报价" :status="stepStatus.quotationStatus"/>
        <el-step title="核保" :status="stepStatus.underwritingStatus"/>
        <el-step title="支付" :status="stepStatus.payStatus"/>
        <el-step title="承保" :status="stepStatus.acceptInsuranceStatus"/>
      </el-steps>
    </div>
    <!-- 表格下拉信息 -->
    <div class="detail_info_container">
      <!-- 报价资料 -->
      <div class="detail_head no_select" >
        <div class="detail_head_item" @click="handleTableExpand('quotationInfo')">
          <i class="img_icon" style="height: 15px; margin-right: 15px;" :style="{ backgroundImage: `url(${showFlag.quotationInfo?expandIcon:collapseIcon})` }"></i>
          <span>报价资料</span>
        </div>
        <div class="detail_head_item" @click="handleEdit(0)" v-if="type=='handle' && canEdit">
          <i class="img_icon" style="height: 25px;" :style="{ backgroundImage: `url(${editIcon})` }"></i>
        </div>
      </div>
      <Transition :css="false" v-bind="collapseTransitionHooks">
      <div class="detail_content" v-if="showFlag.quotationInfo">
        <!-- 保险公司 -->
        <div class="content_row" style="justify-content: space-between;">
          <div>
            <span class="form_text">保险公司:</span>
            <span> {{ oriInfo.insuranceMerchantName }}</span>
          </div>
          <div>
            <span class="form_text">保司工号:</span>
            <span> {{ oriInfo.insuranceMerchantCode }}</span>
          </div>
          <div>
            <span class="form_text">商业起期:</span>
            <span> {{ formatSecondTimestamp(oriInfo.commercialInsuranceStartTime) }}</span>
          </div>
          <div>
            <span class="form_text">交强起期:</span>
            <span> {{ formatSecondTimestamp(oriInfo.compulsoryInsuranceStartTime) }}</span>
          </div>
        </div>
        <div class="divider"></div>
        <!-- 身份证 -->
        <div class="img_detail_show" v-if="oriInfo.ownerType==0">
          <div class="img_detail_show_left">
            <div class="left_img_item">
              <div>证件照头像面</div>
              <el-image v-if="showFileUrl.idCardBack" class="document-preview" :src="showFileUrl.idCardBack" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="证件照头像面未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
            <div class="left_img_item">
              <div>证件照国徽面</div>
              <el-image v-if="showFileUrl.idCardFront" class="document-preview" :src="showFileUrl.idCardFront" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="证件照国徽面未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
          </div>
          <div class="img_detail_show_right form_margin">
            <div>
              <span class="form_text workorder_detail_form_title">姓名:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.ownerName) }">{{ displayDetailValue(oriInfo.ownerName) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">证件号码:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.ownerIdNum) }">{{ displayDetailValue(oriInfo.ownerIdNum) }}</span>
            </div>
          </div>
        </div>
        <!-- 营业执照 -->
        <div class="img_detail_show" v-if="oriInfo.ownerType==1">
          <div class="img_detail_show_left">
            <div class="left_img_item">
              <div>营业执照</div>
              <el-image v-if="showFileUrl.businessLicense" class="document-preview" :src="showFileUrl.businessLicense" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="营业执照未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
          </div>
          <div class="img_detail_show_right form_margin">
            <div>
              <span class="form_text workorder_detail_form_title">单位名称:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.organizationName) }">{{ displayDetailValue(oriInfo.organizationName) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">社会信用代码:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.socialCreditCode) }">{{ displayDetailValue(oriInfo.socialCreditCode) }}</span>
            </div>
          </div>
        </div>
        <!-- 行驶证 -->
        <div class="img_detail_show" v-if="oriInfo.type==0">
          <div class="img_detail_show_left">
            <div class="left_img_item">
              <div>行驶证副页</div>
              <el-image v-if="showFileUrl.licenseBack" class="document-preview" :src="showFileUrl.licenseBack" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="行驶证副页未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
            <div class="left_img_item">
              <div>行驶证正页</div>
              <el-image v-if="showFileUrl.licenseFront" class="document-preview" :src="showFileUrl.licenseFront" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="行驶证正页未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
          </div>
          <div class="img_detail_show_right form_margin">
            <div>
              <span class="form_text workorder_detail_form_title">车牌号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.licensePlate) }">{{ displayDetailValue(oriInfo.vehicleLicense.licensePlate) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车辆类型:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.vehicleType) }">{{ displayDetailValue(oriInfo.vehicleLicense.vehicleType) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">所有人:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.ownerName) }">{{ displayDetailValue(oriInfo.vehicleLicense.ownerName) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">使用性质:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.usageNature) }">{{ displayDetailValue(oriInfo.vehicleLicense.usageNature) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">厂牌型号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.brandModel) }">{{ displayDetailValue(oriInfo.vehicleLicense.brandModel) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车架号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.vehicleCode) }">{{ displayDetailValue(oriInfo.vehicleLicense.vehicleCode) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">发动机号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.engineCode) }">{{ displayDetailValue(oriInfo.vehicleLicense.engineCode) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">注册日期:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleLicense.registrationDate) }">{{ displayDateValue(oriInfo.vehicleLicense.registrationDate) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">发证日期:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleLicense.issueDate) }">{{ displayDateValue(oriInfo.vehicleLicense.issueDate) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">核定载客:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleLicense.seats) }">{{ displayMetricValue(oriInfo.vehicleLicense.seats) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">核定载质量:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleLicense.approvedLoadCapacity) }">{{ displayMetricValue(oriInfo.vehicleLicense.approvedLoadCapacity) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">整备质量:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleLicense.curbWeight) }">{{ displayMetricValue(oriInfo.vehicleLicense.curbWeight) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">是否过户:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleLicense.isTransfer) }">{{ displayTransferValue(oriInfo.vehicleLicense.isTransfer) }}</span>
            </div>
            <div v-if="oriInfo.vehicleLicense.isTransfer==1">
              <span class="form_text workorder_detail_form_title">过户日期:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleLicense.transferDate) }">{{ displayDateValue(oriInfo.vehicleLicense.transferDate) }}</span>
            </div>
          </div>
        </div>
        <!-- 合格证 -->
        <div class="img_detail_show" v-if="oriInfo.type==1">
          <div class="img_detail_show_left">
            <div class="left_img_item">
              <div>合格证</div>
              <el-image v-if="showFileUrl.certificate" class="document-preview" :src="showFileUrl.certificate" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="合格证未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
          </div>
          <div class="img_detail_show_right form_margin">
            <div>
              <span class="form_text workorder_detail_form_title">厂牌型号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleCertificate.brandModel) }">{{ displayDetailValue(oriInfo.vehicleCertificate.brandModel) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车辆类型:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleCertificate.vehicleType) }">{{ displayDetailValue(oriInfo.vehicleCertificate.vehicleType) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车架号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleCertificate.vehicleCode) }">{{ displayDetailValue(oriInfo.vehicleCertificate.vehicleCode) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">发动机号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleCertificate.engineCode) }">{{ displayDetailValue(oriInfo.vehicleCertificate.engineCode) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">整备质量:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleCertificate.curbWeight) }">{{ displayMetricValue(oriInfo.vehicleCertificate.curbWeight) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">核定载客:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleCertificate.seats) }">{{ displayMetricValue(oriInfo.vehicleCertificate.seats) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">排量:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleCertificate.displacement) }">{{ displayMetricValue(oriInfo.vehicleCertificate.displacement) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">核定载质量:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleCertificate.approvedLoadCapacity) }">{{ displayMetricValue(oriInfo.vehicleCertificate.approvedLoadCapacity) }}</span>
            </div>
          </div>
        </div>
        <!-- 购车发票 -->
        <div class="img_detail_show" v-if="oriInfo.type==1">
          <div class="img_detail_show_left">
            <div class="left_img_item">
              <div>购车发票</div>
              <el-image v-if="showFileUrl.invoice" class="document-preview" :src="showFileUrl.invoice" fit="contain" />
              <div v-else class="document-empty" role="status" aria-label="购车发票未上传">
                <el-icon class="document-empty__icon"><Document /></el-icon>
                <span>暂无文件</span>
              </div>
            </div>
          </div>
          <div class="img_detail_show_right form_margin">
            <div>
              <span class="form_text workorder_detail_form_title">发票金额:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleInvoice.invoiceAmount) }">{{ displayMetricValue(oriInfo.vehicleInvoice.invoiceAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">购方名称:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleInvoice.buyerName) }">{{ displayDetailValue(oriInfo.vehicleInvoice.buyerName) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">购方身份证:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleInvoice.buyerIdNum) }">{{ displayDetailValue(oriInfo.vehicleInvoice.buyerIdNum) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车辆类型:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleInvoice.vehicleType) }">{{ displayDetailValue(oriInfo.vehicleInvoice.vehicleType) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">厂牌型号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleInvoice.brandModel) }">{{ displayDetailValue(oriInfo.vehicleInvoice.brandModel) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车架号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleInvoice.vehicleCode) }">{{ displayDetailValue(oriInfo.vehicleInvoice.vehicleCode) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">发动机号:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.vehicleInvoice.engineCode) }">{{ displayDetailValue(oriInfo.vehicleInvoice.engineCode) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">核定载客:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleInvoice.seats) }">{{ displayMetricValue(oriInfo.vehicleInvoice.seats) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">核定载质量:</span>
              <span class="form_gap detail-value" :class="{ 'detail-value--empty': isEmptyMetricValue(oriInfo.vehicleInvoice.approvedLoadCapacity) }">{{ displayMetricValue(oriInfo.vehicleInvoice.approvedLoadCapacity) }}</span>
            </div>
          </div>
        </div>
        <div style="text-align: left; padding: 0 5px;">
          <span class="form_text workorder_detail_form_title">车主电话:</span>
          <span class="detail-value" :class="{ 'detail-value--empty': isEmptyDetailValue(oriInfo.ownerPhone) }">{{ displayDetailValue(oriInfo.ownerPhone) }}</span>
        </div>
        <div class="divider"></div>
        <!-- 投保险种 -->
        <div class="workorder_detail_insurance_container" style="text-align: left; padding: 0 5px;">
          <div class="workorder_detail_insurance_table">
            <div style="font-weight: 700;">商业险投保险种</div>
            <div class="workorder_detail_insurance_table_content">
              <div class="workorder_detail_insurance_table_item" v-for="item in info.insurance.type1" :key="item.id">
                <div style="width: calc((100% - 10px)/2);">{{item.name }}</div>
                <div style="width: calc((100% - 10px)/2); text-align: center;">{{ item.value }}</div>
              </div>
            </div>
          </div>
          <div class="workorder_detail_insurance_table">
            <div style="font-weight: 700;">强制险投保险种</div>
            <div class="workorder_detail_insurance_table_content">
              <div class="workorder_detail_insurance_table_item" v-for="item in info.insurance.type2" :key="item.id">
                <div style="width: calc((100% - 10px)/2);">{{item.name }}</div>
                <div style="width: calc((100% - 10px)/2); text-align: center;">{{ item.value }}</div>
              </div>
            </div>
          </div>
          <div class="workorder_detail_insurance_table">
            <div style="font-weight: 700; ">增值服务投保险种</div>
            <div class="workorder_detail_insurance_table_content">
              <div class="workorder_detail_insurance_table_item" v-for="item in info.insurance.type3" :key="item.id">
                <div style="width: calc((100% - 10px)/2);">{{item.name }}</div>
                <div style="width: calc((100% - 10px)/2); text-align: center;">{{ item.value }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="divider"></div>
        <div style="text-align: left; padding: 0 5px;">
          <div style="font-weight: 700;">备注</div>
          <div style="min-height: 20px;">{{ info.remark }}</div>
        </div>
      </div>
      </Transition>
      <!-- 报价结果 -->
      <div class="detail_head no_select">
        <div class="detail_head_item" @click="handleTableExpand('quotationResult')">
          <i class="img_icon" style="height: 15px; margin-right: 15px;" :style="{ backgroundImage: `url(${showFlag.quotationResult?expandIcon:collapseIcon})` }"></i>
          <span>报价结果</span>
        </div>
        <div class="detail_head_item" @click="handleEdit(1)" v-if="type=='handle' && canEdit">
          <i class="img_icon" style="height: 25px;" :style="{ backgroundImage: `url(${editIcon})` }"></i>
        </div>
      </div>
      <Transition :css="false" v-bind="collapseTransitionHooks">
      <div class="detail_content" v-if="showFlag.quotationResult">
        <div class="img_detail_show" style="margin-top: 20px;">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">商业起期:</span>
              <span class="form_gap">{{ formatSecondTimestamp(oriInfo.commercialInsuranceStartTime) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">交强起期:</span>
              <span class="form_gap">{{ formatSecondTimestamp(oriInfo.compulsoryInsuranceStartTime) }}</span>
            </div>
          </div>
        </div>
        <div class="img_detail_show">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">商业险保费:</span>
              <span class="form_gap">{{ `¥${(oriInfo.commercialAmount!=null&&oriInfo.commercialAmount!=undefined)?oriInfo.commercialAmount:''}` }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">交强险保费:</span>
              <span class="form_gap">{{ `¥${(oriInfo.compulsoryAmount!=null&&oriInfo.compulsoryAmount!=undefined)?oriInfo.compulsoryAmount:''}` }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车船税:</span>
              <span class="form_gap">{{ `¥${(oriInfo.vehicleAndTaxAmount!=null&&oriInfo.vehicleAndTaxAmount!=undefined)?oriInfo.vehicleAndTaxAmount:''}` }}</span>
            </div>
            <div v-if="oriInfo.nonMotorAmount!=undefined && oriInfo.nonMotorAmount!=null">
              <span class="form_text workorder_detail_form_title">非车保费:</span>
              <span class="form_gap">{{ `¥${(oriInfo.nonMotorAmount!=null&&oriInfo.nonMotorAmount!=undefined)?oriInfo.nonMotorAmount:''}` }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">合计金额:</span>
              <span class="form_gap">{{ `¥${computeSumAmount()}` }}</span>
            </div>
          </div>
        </div>
        <div class="img_detail_show">
          <div class="img_detail_show_left form_text">上游政策费用</div>
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">计算方式:</span>
              <span class="form_gap">{{ (oriInfo.upstreamComputeType ?? 0)==0?"税前":"税后" }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">商业比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.upstreamCommercialPercentage, oriInfo.upstreamCommercialAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">交强比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.upstreamCompulsoryPercentage, oriInfo.upstreamCompulsoryAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车船税比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.upstreamVehicleAndVesselTaxPercentage, oriInfo.upstreamVehicleAndVesselTaxAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">非车比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.upstreamNonMotorPercentage, oriInfo.upstreamNonMotorAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">合计金额:</span>
              <span class="form_gap">{{ `¥${computeUpstreamAmount()}` }}</span>
            </div>
          </div>
        </div>
        <div class="img_detail_show">
          <div class="img_detail_show_left form_text">下游政策费用</div>
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">计算方式:</span>
              <span class="form_gap">{{ (oriInfo.downstreamComputeType ?? 0)==0?"税前":"税后" }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">商业比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.downstreamCommercialPercentage, oriInfo.downstreamCommercialAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">交强比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.downstreamCompulsoryPercentage, oriInfo.downstreamCompulsoryAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">车船税比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.downstreamVehicleAndVesselTaxPercentage, oriInfo.downstreamVehicleAndVesselTaxAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">非车比例/金额:</span>
              <span class="form_gap">{{ computePercentageAndAmountStr(oriInfo.downstreamNonMotorPercentage, oriInfo.downstreamNonMotorAmount) }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">合计金额:</span>
              <span class="form_gap">{{ `¥${computeDownstreamAmount()}` }}</span>
            </div>
          </div>
        </div>
        <div class="img_detail_show">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">报价反馈备注:</span>
              <span class="form_gap">{{ oriInfo.quotationRemark }}</span>
            </div>
          </div>
        </div>
        <!-- <div class="img_detail_show">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">核保反馈备注:</span>
              <span class="form_gap">{{ oriInfo.underwritingRemark }}</span>
            </div>
          </div>
        </div> -->
        <div class="img_detail_show">
          <div>
            <div style="text-align: left; margin-bottom: 5px;">
              <span class="form_text workorder_detail_form_title">保司报价单:</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title file_download_text no_select" 
              @click="handleDownload('quotation')" 
              v-if="showFileUrl.quotation != null && showFileUrl.quotation != undefined && showFileUrl.quotation!=''">
                点击下载保司报价单
              </span>
            </div>
          </div>
        </div>        
      </div>
      </Transition>
      <!-- 支付信息 -->
      <div class="detail_head no_select">
        <div class="detail_head_item" @click="handleTableExpand('payment')">
          <i class="img_icon" style="height: 15px; margin-right: 15px;" :style="{ backgroundImage: `url(${showFlag.payment?expandIcon:collapseIcon})` }"></i>
          <span>支付信息</span>
        </div>
        <div class="detail_head_item" @click="handleEdit(7)" v-if="type=='handle' && canEdit">
          <i class="img_icon" style="height: 25px;" :style="{ backgroundImage: `url(${editIcon})` }"></i>
        </div>
      </div>
      <Transition :css="false" v-bind="collapseTransitionHooks">
      <div class="detail_content" v-if="showFlag.payment">
        <div class="img_detail_show" style="margin-top: 20px;">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">收款人:</span>
              <span class="form_gap">{{ oriInfo.payName }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">收款人联系方式:</span>
              <span class="form_gap">{{ oriInfo.payIdNum }}</span>
            </div>
          </div>
        </div>
        <div class="img_detail_show" style="margin-top: 20px;">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">收款人开户行:</span>
              <span class="form_gap">{{ oriInfo.payBank }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">收款人卡号:</span>
              <span class="form_gap">{{ oriInfo.payBankCardNum }}</span>
            </div>
          </div>
        </div>
        <!-- <div class="img_detail_show">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">支付反馈备注:</span>
              <span class="form_gap">{{ info.payRemark }}</span>
            </div>
          </div>
        </div> -->
      </div>
      </Transition>
      <!-- 承保资料 -->
      <div class="detail_head no_select" style="border-bottom: 0;">
        <div class="detail_head_item" @click="handleTableExpand('acceptInsurance')">
          <i class="img_icon" style="height: 15px; margin-right: 15px;" :style="{ backgroundImage: `url(${showFlag.acceptInsurance?expandIcon:collapseIcon})` }"></i>
          <span>承保资料</span>
        </div>
        <div class="detail_head_item" @click="handleEdit(7)" v-if="type=='handle' && canEdit">
          <i class="img_icon" style="height: 25px;" :style="{ backgroundImage: `url(${editIcon})` }"></i>
        </div>
      </div>
      <Transition :css="false" v-bind="collapseTransitionHooks">
      <div class="detail_content" v-if="showFlag.acceptInsurance" style="border-bottom: 0; border-top: 1px solid #DCDFE6;">
        <div class="img_detail_show" style="margin-top: 20px;">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">商业保单号:</span>
              <span class="form_gap">{{ oriInfo.commercialPolicyNo }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">交强保单号:</span>
              <span class="form_gap">{{ oriInfo.compulsoryPolicyNo }}</span>
            </div>
          </div>
        </div>
        <!-- <div class="img_detail_show" style="margin-top: 20px;">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">物流单号:</span>
              <span class="form_gap">{{ oriInfo.trackingNum }}</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title">快递公司:</span>
              <span class="form_gap">{{ oriInfo.logisticsCompany }}</span>
            </div>
          </div>
        </div> -->
        <div class="img_detail_show">
          <div>
            <div style="text-align: left; margin-bottom: 5px;">
              <span class="form_text workorder_detail_form_title">商业保单:</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title file_download_text no_select" 
              @click="handleDownload('acceptInsuranceCommercial')" 
              v-if="showFileUrl.acceptInsuranceCommercial != null && showFileUrl.acceptInsuranceCommercial != undefined && showFileUrl.acceptInsuranceCommercial!=''">
                点击下载商业保单
              </span>
            </div>
          </div>
          <div>
            <div style="text-align: left; margin-bottom: 5px;">
              <span class="form_text workorder_detail_form_title">交强保单:</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title file_download_text no_select" 
              @click="handleDownload('acceptInsuranceCompulsory')" 
              v-if="showFileUrl.acceptInsuranceCompulsory != null && showFileUrl.acceptInsuranceCompulsory != undefined && showFileUrl.acceptInsuranceCompulsory!=''">
                点击下载交强保单
              </span>
            </div>
          </div>
          <div>
            <div style="text-align: left; margin-bottom: 5px;">
              <span class="form_text workorder_detail_form_title">其他单证:</span>
            </div>
            <div>
              <span class="form_text workorder_detail_form_title file_download_text no_select" 
              @click="handleDownload('acceptInsuranceOther')" 
              v-if="showFileUrl.acceptInsuranceOther != null && showFileUrl.acceptInsuranceOther != undefined && showFileUrl.acceptInsuranceOther.length>0">
                点击下载其他单证
              </span>
            </div>
          </div>
        </div> 
        <div class="img_detail_show">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">承保反馈备注:</span>
              <span class="form_gap">{{ oriInfo.acceptInsuranceRemark }}</span>
            </div>
          </div>
        </div>
        <div class="img_detail_show" v-if="oriInfo.status==9">
          <div class="img_detail_show_right">
            <div>
              <span class="form_text workorder_detail_form_title">承保失败反馈:</span>
              <span class="form_gap">{{ oriInfo.acceptInsuranceFailedRemark }}</span>
            </div>
          </div>
        </div>
      </div>
      </Transition>
    </div>
    <!-- 按钮 -->
    <div ref="scrollRef" style="margin: 50px 0 25px 0;" v-show="showFlag.uploadForm">
      <div style="margin-bottom: 10px; display: flex; justify-content: center; align-items: center;">
        <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${writeIcon})` }"></i>
        <div style="font-weight: 700; font-size: 20px; margin-left: 10px;">我的反馈</div>
      </div>
      <div style="margin-bottom: 10px; text-align: left;">请选择反馈状态</div>
      <div class="workorder_form_button_container">
        <div class="workoerder_form_button no_select" v-if="formStatus != 1" @click="handleFormStatusChange(1)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">报价成功</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 1">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">报价成功</div>
        </div>
        <!-- <div class="workoerder_form_button no_select" v-if="formStatus != 2" @click="handleFormStatusChange(2)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">报价失败</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 2">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">报价失败</div>
        </div> -->
        <!-- <div class="workoerder_form_button no_select" v-if="formStatus != 3" @click="handleFormStatusChange(3)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">核保成功</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 3">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">核保成功</div>
        </div> -->
        <!-- <div class="workoerder_form_button no_select" v-if="formStatus != 4" @click="handleFormStatusChange(4)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">核保失败</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 4">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">核保失败</div>
        </div> -->
        <!-- <div class="workoerder_form_button no_select" v-if="formStatus != 5" @click="handleFormStatusChange(5)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">支付待确认</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 5">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">支付待确认</div>
        </div> -->
        <!-- <div class="workoerder_form_button no_select" v-if="formStatus != 6" @click="handleFormStatusChange(6)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">支付失败</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 6">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">支付失败</div>
        </div> -->
        <div class="workoerder_form_button no_select" v-if="formStatus != 7" @click="handleFormStatusChange(7)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">承保成功</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 7">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${successIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">承保成功</div>
        </div>
        <div class="workoerder_form_button no_select" v-if="formStatus != 8" @click="handleFormStatusChange(8)">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileGrayIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">承保失败</div>
        </div>
        <div class="workoerder_form_button_select no_select" v-if="formStatus == 8">
          <i class="img_icon" style="width: 30px;" :style="{ backgroundImage: `url(${faileIcon})` }"></i>
          <div class="no_wrap" style="margin-left: 10px;">承保失败</div>
        </div>
      </div>
    </div>
    <div class="divider" v-if="showFlag.uploadForm"></div>
    <!-- 报价表单 -->
    <div class="workorder_form" v-if="formStatus==1 && showFlag.uploadForm">
      <el-form
        ref="quotationRef"
        :model="info"
        status-icon
        :rules="quotationRules"
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">报价起期</span>
        </el-row>
        <el-row>
          <!-- <el-col :span="12">
            <el-form-item prop="quotationNo" label="报价单号">
              <el-input
              v-model="info.quotationNo"
              class="responsive-input"
              placeholder="请输入报价单号"
              />
            </el-form-item>
          </el-col> -->
          <el-col :span="6">
            <el-form-item prop="commercialInsuranceStartTime" label="商业起期">
              <el-date-picker
                style="width: 100%;"
                v-model="info.commercialInsuranceStartTime"
                type="date"
                placeholder="请选择商业险起保日期"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="compulsoryInsuranceStartTime" label="交强起期">
              <el-date-picker
                style="width: 100%;"
                v-model="info.compulsoryInsuranceStartTime"
                type="date"
                placeholder="请选择交强险起保日期"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">保费</span>
        </el-row>
        <el-row> 
          <el-col :span="6">
            <el-form-item prop="commercialAmount" label="商业保费" v-if="showFlag.insuranceType1">
              <el-input
              v-model="info.commercialAmount"
              placeholder="请输入商业保费"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="compulsoryAmount" label="交强保费" v-if="showFlag.insuranceType3">
              <el-input
              v-model="info.compulsoryAmount"
              placeholder="请输入交强保费"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="vehicleAndTaxAmount" label="车船税" v-if="showFlag.insuranceType3">
              <el-input
              v-model="info.vehicleAndTaxAmount"
              placeholder="请输入车船税"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <div style="color:var(--insurance-primary);" class="no_select" v-if="!showFlag.is_have_non_motor" @click="handleNonMotorChange(true)">+添加非车险险种(最多添加1条)</div>
          </el-col>
        </el-row>
        <el-row v-if="showFlag.is_have_non_motor">
          <!-- <el-col :span="6">
            <el-form-item prop="nonMotorInsuranceName" label="险种名称">
              <el-input
              v-model="info.nonMotorInsuranceName"
              placeholder="请输入险种名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="nonMotorCoverageAmount" label="保额">
              <el-input
              v-model="info.nonMotorCoverageAmount"
              placeholder="请输入保额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col> -->
          <el-col :span="6">
            <el-form-item prop="nonMotorAmount" label="非车险保费">
              <el-input
              v-model="info.nonMotorAmount"
              placeholder="请输入非车险保费"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <div style="color: #F56C6C;" class="no_select" @click="handleNonMotorChange(false)">删除</div>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="6">
            <el-form-item label="合计保费">
              <el-input
              :disabled="true"
              v-model="sumAmount"
              placeholder=""
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">上游政策费用</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="上游费用计算方式">
              <el-radio-group v-model="info.upstreamComputeType">
                <el-radio :value="0">按税前保费计算</el-radio>
                <el-radio :value="1">按税后保费计算</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 上游政策费用 -->
        <el-row>
          <el-col :span="6" v-if="!showFlag.upstream_commercial && showFlag.insuranceType1">
            <el-form-item prop="upstreamCommercialPercentage" label="商业政策比例">
              <el-input
              v-model="info.upstreamCommercialPercentage"
              placeholder="请输入商业政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamCommercialPercentage, info.commercialAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCommercial', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_commercial && showFlag.insuranceType1">
            <el-form-item prop="upstreamCommercialAmount" label="商业政策金额">
              <el-input
              v-model="info.upstreamCommercialAmount"
              placeholder="请输入商业政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCommercial', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.upstream_compulsory && showFlag.insuranceType3">
            <el-form-item prop="upstreamCompulsoryPercentage" label="交强政策比例">
              <el-input
              v-model="info.upstreamCompulsoryPercentage"
              placeholder="请输入交强政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamCompulsoryPercentage, info.compulsoryAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCompulsory', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_compulsory && showFlag.insuranceType3">
            <el-form-item prop="upstreamCompulsoryAmount" label="交强政策金额">
              <el-input
              v-model="info.upstreamCompulsoryAmount"
              placeholder="请输入交强政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCompulsory', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.upstream_tax && showFlag.insuranceType3">
            <el-form-item prop="upstreamVehicleAndVesselTaxPercentage" label="车船税政策比例">
              <el-input
              v-model="info.upstreamVehicleAndVesselTaxPercentage"
              placeholder="请输入车船税政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamVehicleAndVesselTaxPercentage, info.vehicleAndTaxAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamTax', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_tax && showFlag.insuranceType3">
            <el-form-item prop="upstreamVehicleAndVesselTaxAmount" label="车船税政策金额">
              <el-input
              v-model="info.upstreamVehicleAndVesselTaxAmount"
              placeholder="请输入车船税政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamTax', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.upstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="upstreamNonMotorPercentage" label="非车险政策比例">
              <el-input
              v-model="info.upstreamNonMotorPercentage"
              placeholder="请输入非车险政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamNonMotorPercentage, info.nonMotorAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamNonMotor', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="upstreamNonMotorAmount" label="非车险政策金额">
              <el-input
              v-model="info.upstreamNonMotorAmount"
              placeholder="请输入非车险政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamNonMotor', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">下游政策费用</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item prop="test" label="下游费用计算方式">
              <el-radio-group v-model="info.downstreamComputeType">
                <el-radio :value="0">按税前保费计算</el-radio>
                <el-radio :value="1">按税后保费计算</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 下游政策 -->
        <el-row>
          <el-col :span="6" v-if="!showFlag.downstream_commercial && showFlag.insuranceType1">
            <el-form-item prop="downstreamCommercialPercentage" label="商业政策比例">
              <el-input
              v-model="info.downstreamCommercialPercentage"
              placeholder="请输入商业政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamCommercialPercentage, info.commercialAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCommercial', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_commercial && showFlag.insuranceType1">
            <el-form-item prop="downstreamCommercialAmount" label="商业政策金额">
              <el-input
              v-model="info.downstreamCommercialAmount"
              placeholder="请输入商业政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCommercial', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.downstream_compulsory && showFlag.insuranceType3">
            <el-form-item prop="downstreamCompulsoryPercentage" label="交强政策比例">
              <el-input
              v-model="info.downstreamCompulsoryPercentage"
              placeholder="请输入交强政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamCompulsoryPercentage, info.compulsoryAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCompulsory', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_compulsory && showFlag.insuranceType3">
            <el-form-item prop="downstreamCompulsoryAmount" label="交强政策金额">
              <el-input
              v-model="info.downstreamCompulsoryAmount"
              placeholder="请输入交强政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCompulsory', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.downstream_tax && showFlag.insuranceType3">
            <el-form-item prop="downstreamVehicleAndVesselTaxPercentage" label="车船税政策比例">
              <el-input
              v-model="info.downstreamVehicleAndVesselTaxPercentage"
              placeholder="请输入车船税政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamVehicleAndVesselTaxPercentage, info.vehicleAndTaxAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamTax', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_tax && showFlag.insuranceType3">
            <el-form-item prop="downstreamVehicleAndVesselTaxAmount" label="车船税政策金额">
              <el-input
              v-model="info.downstreamVehicleAndVesselTaxAmount"
              placeholder="请输入车船税政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamTax', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.downstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="downstreamNonMotorPercentage" label="非车险政策比例">
              <el-input
              v-model="info.downstreamNonMotorPercentage"
              placeholder="请输入非车险政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamNonMotorPercentage, info.nonMotorAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamNonMotor', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="downstreamNonMotorAmount" label="非车险政策金额">
              <el-input
              v-model="info.downstreamNonMotorAmount"
              placeholder="请输入非车险政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamNonMotor', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row>
          <span class="workoerder_form_big_title">保司报价单</span>
        </el-row>
        <el-row style="min-height: 140px;">
          <el-col :span="6">
            <el-upload
              v-loading="loadingFlag.quotation"
              drag
              :file-list="fileStore.quotationFile"
              action="#" 
              :auto-upload="false" 
              :multiple=false
              :limit="1"
              :on-change="handleQuotationChange"
              :on-remove="(file, files)=>{fileStore.quotationFile = files; workorderFileIds.quotationFile = undefined;}"
            >
              <!-- <el-button type="primary" :disabled="fileStore.quotationFile.length>0">点击上传保司报价单</el-button> -->
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                拖动文件到这里 或者 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                </div>
              </template>
            </el-upload>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row>
          <span class="workoerder_form_big_title">报价反馈备注</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item prop="quotationRemark" label="备注">
              <el-input
                v-model="info.quotationRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入报价反馈备注"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(quotationRef, 'quotation')">提交</el-button>
        <el-button @click="handleResetForm('quotation')">重置</el-button>
      </div>
    </div>
    <!-- 报价失败 -->
    <!-- <div class="workorder_form" v-if="formStatus==2 && showFlag.uploadForm">
      <el-form
        ref="quotationFailedRef"
        :model="info"
        status-icon
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">报价失败原因</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="原因">
              <el-input
                v-model="info.quotationFailedRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入报价失败原因"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(quotationFailedRef, 'quotationFailed')">提交</el-button>
        <el-button @click="handleResetForm('quotationFailed')">重置</el-button>
      </div>
    </div> -->
    <!-- 核保表单 -->
    <!-- <div class="workorder_form" v-if="formStatus==3 && showFlag.uploadForm">
      <el-form
        ref="underwriteRef"
        :model="info"
        status-icon
        :rules="quotationRules"
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">报价起期</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item prop="quotationNo" label="报价单号">
              <el-input
              v-model="info.quotationNo"
              class="responsive-input"
              placeholder="请输入报价单号"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="commercialInsuranceStartTime" label="商业起期">
              <el-date-picker
                style="width: 100%;"
                v-model="info.commercialInsuranceStartTime"
                type="date"
                placeholder="请选择商业险起保日期"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="compulsoryInsuranceStartTime" label="交强起期">
              <el-date-picker
                style="width: 100%;"
                v-model="info.compulsoryInsuranceStartTime"
                type="date"
                placeholder="请选择交强险起保日期"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">保费/政策<span style="font-weight: 700;" class="form_text">(核保价格如有变动请修改保费)</span></span>
        </el-row>
        <el-row> 
          <el-col :span="6">
            <el-form-item prop="commercialAmount" label="商业保费">
              <el-input
              v-model="info.commercialAmount"
              placeholder="请输入商业保费"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="compulsoryAmount" label="交强保费">
              <el-input
              v-model="info.compulsoryAmount"
              placeholder="请输入交强保费"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="vehicleAndTaxAmount" label="车船税">
              <el-input
              v-model="info.vehicleAndTaxAmount"
              placeholder="请输入车船税"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <div style="color:var(--insurance-primary);" class="no_select" v-if="!showFlag.is_have_non_motor" @click="handleNonMotorChange(true)">+添加非车险险种(最多添加1条)</div>
          </el-col>
        </el-row>
        <el-row v-if="showFlag.is_have_non_motor">
          <el-col :span="6">
            <el-form-item prop="nonMotorInsuranceName" label="险种名称">
              <el-input
              v-model="info.nonMotorInsuranceName"
              placeholder="请输入险种名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="nonMotorCoverageAmount" label="保额">
              <el-input
              v-model="info.nonMotorCoverageAmount"
              placeholder="请输入保额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="nonMotorAmount" label="非车险保费">
              <el-input
              v-model="info.nonMotorAmount"
              placeholder="请输入非车险保费"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <div style="color: #F56C6C;" class="no_select" @click="handleNonMotorChange(false)">删除</div>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="6">
            <el-form-item label="合计保费">
              <el-input
              :disabled="true"
              v-model="sumAmount"
              placeholder=""
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">上游政策费用</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="上游费用计算方式">
              <el-radio-group v-model="info.upstreamComputeType">
                <el-radio :value="0">按税前保费计算</el-radio>
                <el-radio :value="1">按税后保费计算</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="6" v-if="!showFlag.upstream_commercial">
            <el-form-item prop="upstreamCommercialPercentage" label="商业政策比例">
              <el-input
              v-model="info.upstreamCommercialPercentage"
              placeholder="请输入商业政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamCommercialPercentage, info.commercialAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCommercial', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_commercial">
            <el-form-item prop="upstreamCommercialAmount" label="商业政策金额">
              <el-input
              v-model="info.upstreamCommercialAmount"
              placeholder="请输入商业政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCommercial', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.upstream_compulsory">
            <el-form-item prop="upstreamCompulsoryPercentage" label="交强政策比例">
              <el-input
              v-model="info.upstreamCompulsoryPercentage"
              placeholder="请输入交强政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamCompulsoryPercentage, info.compulsoryAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCompulsory', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_compulsory">
            <el-form-item prop="upstreamCompulsoryAmount" label="交强政策金额">
              <el-input
              v-model="info.upstreamCompulsoryAmount"
              placeholder="请输入交强政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamCompulsory', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.upstream_tax">
            <el-form-item prop="upstreamVehicleAndVesselTaxPercentage" label="车船税政策比例">
              <el-input
              v-model="info.upstreamVehicleAndVesselTaxPercentage"
              placeholder="请输入车船税政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamVehicleAndVesselTaxPercentage, info.vehicleAndTaxAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamTax', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_tax">
            <el-form-item prop="upstreamVehicleAndVesselTaxAmount" label="车船税政策金额">
              <el-input
              v-model="info.upstreamVehicleAndVesselTaxAmount"
              placeholder="请输入车船税政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamTax', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.upstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="upstreamNonMotorPercentage" label="非车险政策比例">
              <el-input
              v-model="info.upstreamNonMotorPercentage"
              placeholder="请输入非车险政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.upstreamNonMotorPercentage, info.nonMotorAmount, info.upstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamNonMotor', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.upstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="upstreamNonMotorAmount" label="非车险政策金额">
              <el-input
              v-model="info.upstreamNonMotorAmount"
              placeholder="请输入非车险政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('upstreamNonMotor', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">下游政策费用</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item prop="test" label="下游费用计算方式">
              <el-radio-group v-model="info.downstreamComputeType">
                <el-radio :value="0">按税前保费计算</el-radio>
                <el-radio :value="1">按税后保费计算</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="6" v-if="!showFlag.downstream_commercial">
            <el-form-item prop="downstreamCommercialPercentage" label="商业政策比例">
              <el-input
              v-model="info.downstreamCommercialPercentage"
              placeholder="请输入商业政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamCommercialPercentage, info.commercialAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCommercial', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_commercial">
            <el-form-item prop="downstreamCommercialAmount" label="商业政策金额">
              <el-input
              v-model="info.downstreamCommercialAmount"
              placeholder="请输入商业政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCommercial', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.downstream_compulsory">
            <el-form-item prop="downstreamCompulsoryPercentage" label="交强政策比例">
              <el-input
              v-model="info.downstreamCompulsoryPercentage"
              placeholder="请输入交强政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamCompulsoryPercentage, info.compulsoryAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCompulsory', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_compulsory">
            <el-form-item prop="downstreamCompulsoryAmount" label="交强政策金额">
              <el-input
              v-model="info.downstreamCompulsoryAmount"
              placeholder="请输入交强政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamCompulsory', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.downstream_tax">
            <el-form-item prop="downstreamVehicleAndVesselTaxPercentage" label="车船税政策比例">
              <el-input
              v-model="info.downstreamVehicleAndVesselTaxPercentage"
              placeholder="请输入车船税政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamVehicleAndVesselTaxPercentage, info.vehicleAndTaxAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamTax', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_tax">
            <el-form-item prop="downstreamVehicleAndVesselTaxAmount" label="车船税政策金额">
              <el-input
              v-model="info.downstreamVehicleAndVesselTaxAmount"
              placeholder="请输入车船税政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamTax', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="!showFlag.downstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="downstreamNonMotorPercentage" label="非车险政策比例">
              <el-input
              v-model="info.downstreamNonMotorPercentage"
              placeholder="请输入非车险政策比例"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">%</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div>{{ `${percentageMulti(info.downstreamNonMotorPercentage, info.nonMotorAmount, info.downstreamComputeType)}元` }}</div>
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamNonMotor', true)" class="no_select">按金额</div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="6" v-if="showFlag.downstream_non_motor && showFlag.is_have_non_motor">
            <el-form-item prop="downstreamNonMotorAmount" label="非车险政策金额">
              <el-input
              v-model="info.downstreamNonMotorAmount"
              placeholder="请输入非车险政策金额"
              >
                <template #suffix>
                  <span style="margin-right: 8px; color: #666;">元</span>
                </template>
              </el-input>
              <div style="display: flex;">
                <div style="color: var(--insurance-primary); margin-left: 10px;" @click="handleAmountShowChange('downstreamNonMotor', false)" class="no_select">按比例</div>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row>
          <span class="workoerder_form_big_title">保司报价单</span>
        </el-row>
        <el-row style="min-height: 140px;">
          <el-col :span="6">
            <el-upload
              drag
              :file-list="fileStore.quotationFile"
              action="#" 
              :auto-upload="false" 
              :multiple=false
              :limit="1"
              :on-change="handleQuotationChange"
              :on-remove="(file, files)=>{fileStore.quotationFile = files; workorderFileIds.quotationFile = undefined;}"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                拖动文件到这里 或者 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                </div>
              </template>
            </el-upload>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row>
          <span class="workoerder_form_big_title">核保反馈备注</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item prop="underwritingRemark" label="备注">
              <el-input
                v-model="info.underwritingRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入核保反馈备注"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(underwriteRef, 'underwriting')">提交</el-button>
        <el-button @click="handleResetForm('underwriting')">重置</el-button>
      </div>
    </div> -->
    <!-- 核保失败 -->
    <!-- <div class="workorder_form" v-if="formStatus==4 && showFlag.uploadForm">
      <el-form
        ref="underwriteFailedRef"
        :model="info"
        status-icon
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">核保失败原因</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="原因">
              <el-input
                v-model="info.underwritingFailedRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入核保失败原因"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(underwriteFailedRef, 'underwritingFailed')">提交</el-button>
        <el-button @click="handleResetForm('underwritingFailed')">重置</el-button>
      </div>
    </div> -->
    <!-- 支付信息 -->
    <!-- <div class="workorder_form" v-if="formStatus==5 && showFlag.uploadForm">
      <el-form
        ref="paymentRef"
        :model="info"
        status-icon
        :rules="payRules"
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">金额</span>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="requiredPayAmount" label="需要支付保费">
              <el-input
              v-model="info.requiredPayAmount"
              placeholder="请输入需要支付保费"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <span class="workoerder_form_big_title">收款信息</span>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="payName" label="收款人">
              <el-input
              v-model="info.payName"
              placeholder="请输入收款人"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="payIdNum" label="收款人联系方式">
              <el-input
              v-model="info.payIdNum"
              placeholder="请输入收款人联系方式"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="payBank" label="收款人开户行">
              <el-input
              v-model="info.payBank"
              placeholder="请输入收款人开户行"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="payBankCardNum" label="银行卡号">
              <el-input
              v-model="info.payBankCardNum"
              placeholder="请输入银行卡号"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row :gutter="20">
          <span class="workoerder_form_big_title">支付反馈备注</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input
                v-model="info.payRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入支付反馈备注"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(paymentRef, 'pay')">提交</el-button>
        <el-button @click="handleResetForm('pay')">重置</el-button>
      </div>
    </div> -->
    <!-- 支付失败 -->
    <!-- <div class="workorder_form" v-if="formStatus==6 && showFlag.uploadForm">
      <el-form
        ref="paymentFailedRef"
        :model="info"
        status-icon
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">支付失败原因</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="原因">
              <el-input
                v-model="info.payFailedRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入支付失败原因"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(paymentFailedRef, 'payFailed')">提交</el-button>
        <el-button @click="handleResetForm('payFailed')">重置</el-button>
      </div>
    </div> -->
    <!-- 承保表单 -->
    <div class="workorder_form" v-if="formStatus==7 && showFlag.uploadForm">
      <el-form
        ref="acceptInsuranceRef"
        :model="info"
        status-icon
        :rules="acceptInsuranceRules"
        label-width="auto"
        size="large"
        class="login_form"
      >
        <el-row>
          <span class="workoerder_form_big_title">收款信息</span>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="payName" label="收款人">
              <el-input
              v-model="info.payName"
              placeholder="请输入收款人"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="payIdNum" label="收款人联系方式">
              <el-input
              v-model="info.payIdNum"
              placeholder="请输入收款人联系方式"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="payBank" label="收款人开户行">
              <el-input
              v-model="info.payBank"
              placeholder="请输入收款人开户行"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="payBankCardNum" label="银行卡号">
              <el-input
              v-model="info.payBankCardNum"
              placeholder="请输入银行卡号"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row>
          <span class="workoerder_form_big_title">登记保单信息</span>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="commercialPolicyNo" label="商业保单号">
              <el-input
              v-model="info.commercialPolicyNo"
              placeholder="请输入商业保单号"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="compulsoryPolicyNo" label="交强保单号">
              <el-input
              v-model="info.compulsoryPolicyNo"
              placeholder="请输入交强保单号"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item prop="trackingNum" label="物流单号">
              <el-input
              v-model="info.trackingNum"
              placeholder="请输入物流单号"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item prop="logisticsCompany" label="快递公司">
              <el-input
              v-model="info.logisticsCompany"
              placeholder="请输入快递公司"
              />
            </el-form-item>
          </el-col>
        </el-row> -->
        <div class="divider"></div>
        <el-row>
          <span class="workoerder_form_big_title">电子保单</span>
        </el-row>
        <el-row style="min-height: 140px;">
          <el-col :span="6">
            <div class="workorder_file_title">商业</div>
            <el-upload
              v-loading="loadingFlag.commercial"
              drag
              :file-list="fileStore.acceptInsuranceCommercialFile"
              action="#" 
              :auto-upload="false" 
              :multiple=false
              :limit="1"
              :on-change="handleAcceptInsuranceCommercialChange"
              :on-remove="(file, files)=>{fileStore.acceptInsuranceCommercialFile = files; workorderFileIds.acceptInsuranceCommercialFile = undefined;}"
            >
              <!-- <el-button type="primary" :disabled="fileStore.acceptInsuranceCommercialFile.length>0">点击上传商业电子保单</el-button> -->
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                拖动文件到这里 或者 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                </div>
              </template>
            </el-upload>
          </el-col>
          <el-col :span="1"></el-col>
          <el-col :span="6">
            <div class="workorder_file_title">交强</div>
            <el-upload
              v-loading="loadingFlag.compulsory"
              drag
              :file-list="fileStore.acceptInsuranceCompulsoryFile"
              action="#" 
              :auto-upload="false" 
              :multiple=false
              :limit="1"
              :on-change="handleAcceptInsuranceCompulsoryChange"
              :on-remove="(file, files)=>{fileStore.acceptInsuranceCompulsoryFile = files; workorderFileIds.acceptInsuranceCompulsoryFile = undefined;}"
            >
              <!-- <el-button type="primary" :disabled="fileStore.acceptInsuranceCompulsoryFile.length>0">点击上传交强电子保单</el-button> -->
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                拖动文件到这里 或者 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                </div>
              </template>
            </el-upload>
          </el-col>
        </el-row>
        <el-row style="min-height: 140px;">
          <el-col :span="6">
            <div class="workorder_file_title">其他单证 <span class="form_text">(最多上传6个文件)</span></div>
            <el-upload
              v-loading="loadingFlag.other!=0"
              drag
              :file-list="fileStore.acceptInsuranceOtherFile"
              action="#" 
              :auto-upload="false"
              :multiple=true
              :limit="6"
              :on-change="handleAcceptInsuranceOtherChange"
              :on-remove="handleAcceptInsuranceOtherRemove"
            >
              <!-- <el-button type="primary" :disabled="fileStore.acceptInsuranceOtherFile.length>=6">点击上传其他单证</el-button> -->
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                拖动文件到这里 或者 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                </div>
              </template>
            </el-upload>
          </el-col>
        </el-row>
        <div class="divider"></div>
        <el-row :gutter="20">
          <span class="workoerder_form_big_title">承保反馈备注</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item prop="acceptInsuranceRemark" label="备注">
              <el-input
                v-model="info.acceptInsuranceRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入承保反馈备注"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(acceptInsuranceRef, 'acceptInsurance')">提交</el-button>
        <el-button @click="handleResetForm('acceptInsurance')">重置</el-button>
      </div>
    </div>
    <!-- 承保失败 -->
    <div class="workorder_form" v-if="formStatus==8 && showFlag.uploadForm">
      <el-form
        ref="acceptInsuranceFailedRef"
        :model="info"
        status-icon
        label-width="auto"
        size="large"
        class="login_form"
      > 
        <el-row>
          <span class="workoerder_form_big_title">承保失败原因</span>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="原因">
              <el-input
                v-model="info.acceptInsuranceFailedRemark"
                resize="none"
                style="width: 100%;"
                :rows="8"
                type="textarea"
                placeholder="请输入承保失败原因"
                maxlength="400"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div>
        <el-button type="primary" @click="handleSubmit(acceptInsuranceFailedRef, 'acceptInsuranceFailed')">提交</el-button>
        <el-button @click="handleResetForm('acceptInsuranceFailed')">重置</el-button>
      </div>
    </div>
  </div>
  <el-drawer v-model="drawer" :with-header="false" size="40%" v-if="drawer">
    <EditBaseWorkorder type="update" :id="id" @refresh="handleRefresh"></EditBaseWorkorder>
  </el-drawer>
</template>

<script setup>
import { reactive, ref, onMounted, computed, nextTick } from 'vue';
import successIcon from '@/assets/icons/success.png';
import faileIcon from '@/assets/icons/faile.png';
import expandIcon from '@/assets/icons/expand.png';
import editIcon from '@/assets/icons/edit.png';
import successGrayIcon from '@/assets/icons/success_gray.png';
import faileGrayIcon from '@/assets/icons/faile_gray.png';
import writeIcon from '@/assets/icons/write.png';
import collapseIcon from '@/assets/icons/collapse.png';
import { convertDateToSecondTimestamp, formatSecondTimestamp } from '@/utils/time';
import { selectWorkorderById } from '@/api/workorder';
import { selectAllInsurance } from '@/api/insurance';
import { downloadByUrl, uploadToOss } from '@/api/file';
import { validateAmount, validFileSize } from '@/utils/validate';
import { updateQuotation, updateNoCascade, updateAcceptInsurance } from '@/api/workorder';
import Message from '@/utils/message';
import { Document, UploadFilled } from '@element-plus/icons-vue';
import EditBaseWorkorder from './EditBaseWorkorder.vue';
import Loading from '@/utils/loading';
import { useRouter } from 'vue-router';

const router = useRouter();

const collapseAnimations = new WeakMap();

/**
 * 清理当前区块的高度动画及临时布局样式。Vue 在连续点击导致过渡取消时也会调用此函数，
 * 以免旧动画继续改变新一轮展开或收起的高度。
 */
const resetCollapseStyles = (element) => {
  collapseAnimations.get(element)?.cancel();
  collapseAnimations.delete(element);
  element.style.height = '';
  element.style.opacity = '';
  element.style.overflow = '';
  element.style.minHeight = '';
  element.style.boxSizing = '';
};

/**
 * 以浏览器动画的完成事件驱动 Vue 过渡结束，而非按估算时长提前卸载节点。
 * 高度从当前像素值变化到内容高度或零；原有 v-if 与 showFlag 业务开关保持不变。
 */
const collapseTransitionHooks = {
  onBeforeEnter(element) {
    element.style.height = '0';
    element.style.opacity = '0';
    element.style.overflow = 'hidden';
    element.style.minHeight = '0';
    element.style.boxSizing = 'border-box';
  },
  onEnter(element, done) {
    const targetHeight = element.scrollHeight;
    const duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 280;
    const animation = element.animate(
      [{ height: '0px', opacity: 0 }, { height: `${targetHeight}px`, opacity: 1 }],
      { duration, easing: 'ease-in-out', fill: 'forwards' }
    );
    collapseAnimations.set(element, animation);
    animation.onfinish = () => {
      resetCollapseStyles(element);
      done();
    };
  },
  onEnterCancelled: resetCollapseStyles,
  onBeforeLeave(element) {
    element.style.height = `${element.getBoundingClientRect().height}px`;
    element.style.opacity = '1';
    element.style.overflow = 'hidden';
    element.style.minHeight = '0';
    element.style.boxSizing = 'border-box';
  },
  onLeave(element, done) {
    const startHeight = element.getBoundingClientRect().height;
    const style = window.getComputedStyle(element);
    const duration = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : 280;
    const animation = element.animate(
      [
        {
          height: `${startHeight}px`,
          paddingTop: style.paddingTop,
          paddingBottom: style.paddingBottom,
          borderTopWidth: style.borderTopWidth,
          borderBottomWidth: style.borderBottomWidth,
          opacity: 1,
        },
        {
          height: '0px',
          paddingTop: '0px',
          paddingBottom: '0px',
          borderTopWidth: '0px',
          borderBottomWidth: '0px',
          opacity: 0,
        },
      ],
      { duration, easing: 'ease-in-out', fill: 'forwards' }
    );
    collapseAnimations.set(element, animation);
    animation.onfinish = () => {
      // 动画保留在零高度终态，Vue 移除节点后再由 onAfterLeave 清理样式。
      done();
    };
  },
  onAfterLeave: resetCollapseStyles,
  onLeaveCancelled: resetCollapseStyles,
};

/**
 * 判断详情文本是否缺失。接口中的 null、undefined 和只包含空白的字符串都代表未录入；
 * 数值 0 对普通文本仍是有效值，避免影响状态、比例等允许为零的业务字段。
 *
 * @param {*} value 后端详情接口返回的原始字段值
 * @returns {boolean} 是否应按暂无信息展示
 */
const isEmptyDetailValue = (value) => (
  value === null || value === undefined || (typeof value === 'string' && value.trim() === '')
);

/**
 * 将缺失文本统一转换为短横线占位，使字段标签保持稳定对齐，同时避免大片“暂无信息”文案造成噪声。
 *
 * @param {*} value 后端详情接口返回的原始字段值
 * @returns {*} 原值或用于空状态的短横线
 */
const displayDetailValue = (value) => isEmptyDetailValue(value) ? '—' : value;

/**
 * 判断车辆重量、排量、座位及金额等正数指标是否未填写。这些字段的默认值 0 不具备实际业务含义，
 * 因而与 null 和空字符串一起按缺失处理；真实的正数值保持原样展示。
 *
 * @param {*} value 车辆证件或发票中的数值字段
 * @returns {boolean} 是否属于无意义的空指标
 */
const isEmptyMetricValue = (value) => isEmptyDetailValue(value) || Number(value) === 0;

/**
 * 格式化车辆正数指标的详情展示。
 *
 * @param {*} value 车辆证件或发票中的数值字段
 * @returns {*} 有效指标值或空状态短横线
 */
const displayMetricValue = (value) => isEmptyMetricValue(value) ? '—' : value;

/**
 * 日期字段仅在存在有效秒级时间戳时执行格式化，避免空值被转换成 1970 年或无效日期。
 *
 * @param {*} value 后端返回的秒级时间戳
 * @returns {string} 格式化日期或空状态短横线
 */
const displayDateValue = (value) => isEmptyMetricValue(value) ? '—' : formatSecondTimestamp(value);

/**
 * 过户状态只有明确返回 0 或 1 时才展示业务结论，未录入时不擅自解释为“非过户”。
 *
 * @param {*} value 后端返回的过户状态
 * @returns {string} 过户状态文案或空状态短横线
 */
const displayTransferValue = (value) => {
  if (isEmptyDetailValue(value)) return '—';
  return Number(value) === 1 ? '过户' : '非过户';
};

const stepStatus = computed(() => {
  let data = {
    nowStep: 0,
    quotationStatus: "",
    underwritingStatus: "",
    payStatus: "",
    acceptInsuranceStatus: ""
  };
  if(oriInfo.value.status == null || oriInfo.value.status == undefined){
    return data;
  }
  if(oriInfo.value.status == 2){
    data.nowStep = 0;
  }
  if(oriInfo.value.status == 3){
    data.nowStep = 0;
    data.quotationStatus = "error";
  }
  if(oriInfo.value.status == 4){
    data.nowStep = 1;
  }
  if(oriInfo.value.status == 5){
    data.nowStep = 1;
    data.underwritingStatus = "error";
  }
  if(oriInfo.value.status == 6){
    data.nowStep = 2;
  }
  if(oriInfo.value.status == 7){
    data.nowStep = 2;
    data.payStatus = "error";
  }
  if(oriInfo.value.status == 8){
    data.nowStep = 3;
  }
  if(oriInfo.value.status == 9){
    data.nowStep = 3;
    data.acceptInsuranceStatus = "error";
  }
  if(oriInfo.value.status == 10){
    data.nowStep = 4;
  }
  return data;
});

const loadingFlag = reactive({
  quotation: false,
  commercial: false,
  compulsory: false,
  other: 0
});

const drawer = ref(false);
const scrollRef = ref();
const formStatus = ref(1);
const type = ref("look");
let id = null;
const fileStore = reactive({
  quotationFile: [],
  acceptInsuranceCommercialFile: [],
  acceptInsuranceCompulsoryFile: [],
  acceptInsuranceOtherFile: []
});
const workorderFileIds = reactive({
  quotationFile: undefined,
  acceptInsuranceCommercialFile: undefined,
  acceptInsuranceCompulsoryFile: undefined,
  acceptInsuranceOtherFile: []
});

const showFileUrl = reactive({
  idCardFront: "",
  idCardBack: "",
  businessLicense: "",
  licenseFront: "",
  licenseBack: "",
  invoice: "",
  certificate: "",
  quotation: "",
  acceptInsuranceCommercial: "",
  acceptInsuranceCompulsory: "",
  acceptInsuranceOther: []
})

const canEdit = computed(() => {
  if(oriInfo.value.status >= 2){
    return true;
  }
  return false;
});

// const quotationFailedRef = ref();
// const underwriteRef = ref();
// const underwriteFailedRef = ref();
// const paymentFailedRef = ref();
const quotationRef = ref();
// const paymentRef = ref();
const acceptInsuranceRef = ref();
const acceptInsuranceFailedRef = ref();

const showFlag = reactive({
  uploadForm: false,
  is_have_non_motor: false,
  upstream_commercial: false,
  upstream_compulsory: false,
  upstream_tax: false,
  upstream_non_motor: false,
  downstream_commercial: false,
  downstream_compulsory: false,
  downstream_tax: false,
  downstream_non_motor: false,
  quotationInfo: true,
  quotationResult: false,
  payment: false,
  acceptInsurance: false,
  insuranceType1: true,
  insuranceType3: true
});

const oriInfo = ref({
  type: 0,
  ownerType: 0,
  createUserName: '',
  createUserPhone: '',
  createMerchantName: '',
  createMerchantCode: '',
  handleMerchantName: '',
  handleMerchantCode: '',
  handleUserName: '',
  code: '',
  createTime: '',
  updateTime: '',
  insuranceMerchantName: '',
  insuranceMerchantCode: '',
  commercialInsuranceStartTime: '',
  compulsoryInsuranceStartTime: '',
  ownerName: '',
  ownerIdNum: '',
  organizationName: '',
  socialCreditCode: '',
  status: 0,
  vehicleLicense: {
    licensePlate: '',
    vehicleType: '',
    ownerName: '',
    usageNature: '',
    brandModel: '',
    vehicleCode: '',
    engineCode: '',
    registrationDate: '',
    issueDate: '',
    seats: '',
    approvedLoadCapacity: '',
    curbWeight: '',
    isTransfer: '',
    transferDate: ''
  },
  vehicleCertificate: {
    brandModel: '',
    vehicleType: '',
    vehicleCode: '',
    engineCode: '',
    curbWeight: '',
    seats: '',
    displacement: '',
    approvedLoadCapacity: ''
  },
  vehicleInvoice: {
    invoiceAmount: '',
    buyerName: '',
    buyerIdNum: '',
    vehicleType: '',
    brandModel: '',
    vehicleCode: '',
    engineCode: '',
    seats: '',
    approvedLoadCapacity: ''
  },
  ownerPhone: '',
  commercialAmount: '',
  compulsoryAmount: '',
  vehicleAndTaxAmount: '',
  nonMotorAmount: '',
  upstreamComputeType: '',
  upstreamCommercialPercentage: '',
  upstreamCommercialAmount: '',
  upstreamCompulsoryPercentage: '',
  upstreamCompulsoryAmount: '',
  upstreamVehicleAndVesselTaxPercentage: '',
  upstreamVehicleAndVesselTaxAmount: '',
  upstreamNonMotorPercentage: '',
  upstreamNonMotorAmount: '',
  upstreamSumAmount: '',
  downstreamComputeType: '',
  downstreamCommercialPercentage: '',
  downstreamCommercialAmount: '',
  downstreamCompulsoryPercentage: '',
  downstreamCompulsoryAmount: '',
  downstreamVehicleAndVesselTaxPercentage: '',
  downstreamVehicleAndVesselTaxAmount: '',
  downstreamNonMotorPercentage: '',
  downstreamNonMotorAmount: '',
  downstreamSumAmount: '',
  payName: '',
  payIdNum: '',
  payBank: '',
  payBankCardNum: '',
  commercialPolicyNo: '',
  compulsoryPolicyNo: '',
  trackingNum: '',
  logisticsCompany: '',
  quotationRemark: '',
  underwritingRemark: '',
  paymentRemark: '',
  acceptInsuranceRemark: '',
  acceptInsuranceFailedRemark: '',
  payRemark: ''
});

const info = reactive({
  commercialInsuranceStartTime: new Date(),
  compulsoryInsuranceStartTime: new Date(),
  quotationNo: "",
  nonMotorInsuranceName: "",
  nonMotorCoverageAmount: undefined,
  commercialAmount: undefined,
  compulsoryAmount: undefined,
  vehicleAndTaxAmount: undefined,
  nonMotorAmount: undefined,
  sumAmount: undefined,
  upstreamComputeType: 0,
  downstreamComputeType: 0,
  upstreamCommercialPercentage: undefined,
  upstreamCompulsoryPercentage: undefined,
  upstreamVehicleAndVesselTaxPercentage: undefined,
  upstreamNonMotorPercentage: undefined,
  upstreamCommercialAmount: undefined,
  upstreamCompulsoryAmount: undefined,
  upstreamVehicleAndVesselTaxAmount: undefined,
  upstreamNonMotorAmount: undefined,
  downstreamCommercialPercentage: undefined,
  downstreamCompulsoryPercentage: undefined,
  downstreamVehicleAndVesselTaxPercentage: undefined,
  downstreamNonMotorPercentage: undefined,
  downstreamCommercialAmount: undefined,
  downstreamCompulsoryAmount: undefined,
  downstreamVehicleAndVesselTaxAmount: undefined,
  downstreamNonMotorAmount: undefined,
  quotationRemark: "",
  quotationFailedRemark: "",
  underwritingRemark: "",
  underwritingFailedRemark: "",
  requiredPayAmount: undefined,
  payName: "",
  payIdNum: "",
  payBank: "",
  payBankCardNum: "",
  payRemark: "",
  payFailedRemark: "",
  commercialPolicyNo: "",
  compulsoryPolicyNo: "",
  trackingNum: "",
  logisticsCompany: "",
  acceptInsuranceRemark: "",
  acceptInsuranceFailedRemark: "",
  status: 0,
  insurance: {
    type1: [],
    type2: [],
    type3: [],
  }
});

const insuranceOptions = ref({
  type1: [],
  type2: [],
  type3: []
});


const quotationRules = {
  quotationRemark: [
    { min: 0, max: 495, message: '输入内容过长', trigger: 'blur' }
  ],
  underwritingRemark: [
    { min: 0, max: 495, message: '输入内容过长', trigger: 'blur' }
  ],
  nonMotorInsuranceName: [
    { required: true, message: '请输入险种名称', trigger: 'blur' },
    { min: 1, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  nonMotorCoverageAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  quotationNo: [
    { required: true, message: '请输入报价单号', trigger: 'blur' },
    { min: 1, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  commercialInsuranceStartTime: [
    { required: true, message: '请选择商业起期', trigger: 'blur' },
  ],
  compulsoryInsuranceStartTime: [
    { required: true, message: '请选择交强起期', trigger: 'blur' },
  ],
  commercialAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  compulsoryAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  vehicleAndTaxAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  nonMotorAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamCommercialAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamCommercialPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamCompulsoryAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamCompulsoryPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamVehicleAndVesselTaxAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamVehicleAndVesselTaxPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamNonMotorAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  upstreamNonMotorPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamCommercialAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamCommercialPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamCompulsoryAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamCompulsoryPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamVehicleAndVesselTaxAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamVehicleAndVesselTaxPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamNonMotorAmount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
  downstreamNonMotorPercentage: [
    { required: true, message: '请输入比例', trigger: 'blur' },
    { validator: validateAmount, trigger: 'blur' }
  ],
}

// const payRules = {
//   requiredPayAmount: [
//     { required: true, message: '请输入金额', trigger: 'blur' },
//     { validator: validateAmount, trigger: 'blur' }
//   ],
//   payName: [
//     { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
//   ],
//   payIdNum: [
//     { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
//   ],
//   payBank: [
//     { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
//   ],
//   payBankCardNum: [
//     { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
//   ]
// }

const acceptInsuranceRules = {
  commercialPolicyNo: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  compulsoryPolicyNo: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  trackingNum: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  logisticsCompany: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
    payName: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  payIdNum: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  payBank: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ],
  payBankCardNum: [
    { min: 0, max: 95, message: '输入内容过长', trigger: 'blur' }
  ]
}

/**

 * * 将工单状态 1 至 10 映射为页头业务阶段文案，未知状态按“待处理”安全展示。

 */
const computeHeadInfo = () => {
  switch (oriInfo.value.status) {
    case 1:
      return '待处理';
    case 2:
      return '待报价';
    case 3:
      return '报价失败';
    case 4:
      return '待核保';
    case 5:
      return '核保失败';
    case 6:
      return '支付待确认';
    case 7:
      return '支付失败';
    case 8:
      return '待承保';
    case 9:
      return '承保失败'
    case 10:
      return '已完成'
    default:
      return '待处理';
  }
}

/**

 * * 报价、核保、支付和承保失败状态使用失败图标，其余进行中或成功状态使用正常图标。

 */
const computeHeadIcon = () => {
  let failedStatus = [3,5,7,9];
  if(failedStatus.includes(oriInfo.value.status)){
    return faileIcon;
  }
  return successIcon;
}
const headTitle = computed(computeHeadInfo);

const headIcon = computed(computeHeadIcon);

/**

 * * 根据是否属于四个失败状态计算页头主题色，使失败结果在视觉上与正常流程区分。

 */
const statusColor = computed(()=> {
  let failedStatus = [3,5,7,9];
  if(failedStatus.includes(oriInfo.value.status)){
    return "#F56C6C";
  }
  return "#1F89FF";
});

/**

 * * 按险种类型把基础字典分成商业险、交强险和附加险三组，供详情按历史投保项反查名称。

 */
const buildInsuranceOption = (data) => {
  let options = {};
  let type1 = data.filter(item => item.type==1);
  let type2 = data.filter(item => item.type==2);
  let type3 = data.filter(item => item.type==3);
  options.type1 = type1 != null ? type1 : [];
  options.type2 = type2 != null ? type2 : [];
  options.type3 = type3 != null ? type3 : [];
  insuranceOptions.value = options;
}

/**
 * 汇总当前表单中的商业险、交强险、车船税和非车险金额。
 * 所有字段均为空或非数字时返回空串，避免页面把“尚未报价”误显示为零元。
 */
const sumAmount = computed(() => {
  let sum = 0;
  let flag = 0;
  if(!isNaN(Number(info.commercialAmount))){
    sum += Number(info.commercialAmount)
    flag++;
  }
  if(!isNaN(Number(info.compulsoryAmount))){
    sum += Number(info.compulsoryAmount)
    flag++;
  }
  if(!isNaN(Number(info.vehicleAndTaxAmount))){
    sum += Number(info.vehicleAndTaxAmount)
    flag++;
  }
  if(!isNaN(Number(info.nonMotorAmount))){
    sum += Number(info.nonMotorAmount)
    flag++;
  }
  if(flag == 0){
    return "";
  }
  else {
    return sum.toFixed(2);
  }
});

/**

 * * 查询险种基础字典并建立类型分组，必须在历史投保险种回填之前完成。

 */
const getAllInsuranceOption = async () => {
  let res = await selectAllInsurance();
  res = res.data;
  if (res.code == 200){
    buildInsuranceOption(res.data);
  }
}

/**
 * 从 sessionStorage 恢复详情查看模式和工单 ID，再并行加载险种字典与工单数据并构建页面。
 * 初始化遮罩覆盖全部异步步骤，并在异常情况下可靠关闭。
 */
onMounted(async () => {
  try{
    Loading.open();
    let nowType = sessionStorage.getItem('workorderDetailType');
    let nowId = sessionStorage.getItem('workorderId');
    if(nowType != null && nowType != undefined && nowType != ''){
      type.value = nowType;
    }
    if(nowId != null && nowId != undefined){
      id = nowId
    }
    await buildPage();
  }
  finally{
    Loading.close();
  }
});

/**

 * * 并行获取险种字典和工单详情，二者完成后统一回填页面，避免险种尚未加载时丢失历史选择。

 */
const buildPage = async () => {
  await Promise.all([
    getAllInsuranceOption(),
    getDataById()
  ]);
  buildInfo();
}

/**
 * 将工单聚合详情恢复为流程表单与展示状态。
 * 该过程回填报价、上下游计算方式、支付和承保资料，重建各类附件预览及文件 ID；费用输入模式优先
 * 尊重历史比例，只有明确存在固定金额且比例缺失时才使用金额，两者都缺失时统一回退为比例。
 */
const buildInfo = () => {
  console.log(oriInfo.value);
  info.upstreamComputeType = oriInfo.value.upstreamComputeType ?? 0;
  info.downstreamComputeType = oriInfo.value.downstreamComputeType ?? 0;
  info.commercialInsuranceStartTime = new Date(oriInfo.value.commercialInsuranceStartTime * 1000);
  info.compulsoryInsuranceStartTime = new Date(oriInfo.value.compulsoryInsuranceStartTime * 1000);
  info.quotationNo = oriInfo.value.quotationNo;
  info.nonMotorInsuranceName = oriInfo.value.nonMotorInsuranceName;
  info.nonMotorCoverageAmount = oriInfo.value.nonMotorCoverageAmount;
  info.commercialAmount = oriInfo.value.commercialAmount;
  info.compulsoryAmount = oriInfo.value.compulsoryAmount;
  info.vehicleAndTaxAmount = oriInfo.value.vehicleAndTaxAmount;
  info.nonMotorAmount = oriInfo.value.nonMotorAmount;
  info.upstreamCommercialPercentage = oriInfo.value.upstreamCommercialPercentage;
  info.upstreamCompulsoryPercentage = oriInfo.value.upstreamCompulsoryPercentage;
  info.upstreamVehicleAndVesselTaxPercentage = oriInfo.value.upstreamVehicleAndVesselTaxPercentage;
  info.upstreamNonMotorPercentage = oriInfo.value.upstreamNonMotorPercentage;
  info.upstreamCommercialAmount = oriInfo.value.upstreamCommercialAmount;
  info.upstreamCompulsoryAmount = oriInfo.value.upstreamCompulsoryAmount;
  info.upstreamVehicleAndVesselTaxAmount = oriInfo.value.upstreamVehicleAndVesselTaxAmount;
  info.upstreamNonMotorAmount = oriInfo.value.upstreamNonMotorAmount;
  info.downstreamCommercialPercentage = oriInfo.value.downstreamCommercialPercentage;
  info.downstreamCompulsoryPercentage = oriInfo.value.downstreamCompulsoryPercentage;
  info.downstreamVehicleAndVesselTaxPercentage = oriInfo.value.downstreamVehicleAndVesselTaxPercentage;
  info.downstreamNonMotorPercentage = oriInfo.value.downstreamNonMotorPercentage;
  info.downstreamCommercialAmount = oriInfo.value.downstreamCommercialAmount;
  info.downstreamCompulsoryAmount = oriInfo.value.downstreamCompulsoryAmount;
  info.downstreamVehicleAndVesselTaxAmount = oriInfo.value.downstreamVehicleAndVesselTaxAmount;
  info.downstreamNonMotorAmount = oriInfo.value.downstreamNonMotorAmount;
  info.quotationRemark = oriInfo.value.quotationRemark;
  info.quotationFailedRemark = oriInfo.value.quotationFailedRemark;
  info.underwritingRemark = oriInfo.value.underwritingRemark;
  info.underwritingFailedRemark = oriInfo.value.underwritingFailedRemark;
  info.requiredPayAmount = oriInfo.value.requiredPayAmount;
  info.payName = oriInfo.value.payName;
  info.payIdNum = oriInfo.value.payIdNum;
  info.payBank = oriInfo.value.payBank;
  info.payBankCardNum = oriInfo.value.payBankCardNum;
  info.payRemark = oriInfo.value.payRemark;
  info.payFailedRemark = oriInfo.value.payFailedRemark;
  info.commercialPolicyNo = oriInfo.value.commercialPolicyNo;
  info.compulsoryPolicyNo = oriInfo.value.compulsoryPolicyNo;
  info.trackingNum = oriInfo.value.trackingNum;
  info.logisticsCompany = oriInfo.value.logisticsCompany;
  info.acceptInsuranceRemark = oriInfo.value.acceptInsuranceRemark;
  info.acceptInsuranceFailedRemark = oriInfo.value.acceptInsuranceFailedRemark;
  info.status = oriInfo.value.status;

  workorderFileIds.quotationFile = undefined;
  workorderFileIds.acceptInsuranceCommercialFile = undefined;
  workorderFileIds.acceptInsuranceCompulsoryFile = undefined;
  workorderFileIds.acceptInsuranceOtherFile = [];

  showFileUrl.idCardFront = "";
  showFileUrl.idCardBack = "";
  showFileUrl.businessLicense = "";
  showFileUrl.licenseFront = "";
  showFileUrl.licenseBack = "";
  showFileUrl.invoice = "";
  showFileUrl.certificate = "";
  showFileUrl.quotation = "";
  showFileUrl.acceptInsuranceCommercial = "";
  showFileUrl.acceptInsuranceCompulsory = "";
  showFileUrl.acceptInsuranceOther = [];

  fileStore.quotationFile = [];
  fileStore.acceptInsuranceCommercialFile = [];
  fileStore.acceptInsuranceCompulsoryFile = [];
  fileStore.acceptInsuranceOtherFile = [];

  if (oriInfo.value.workorderFileList != null && oriInfo.value.workorderFileList != undefined){
    oriInfo.value.workorderFileList.forEach(item => {
      if (item.type=="idCardFront"){
        showFileUrl.idCardFront = item.path;
      }
      if (item.type=="idCardBack"){
        showFileUrl.idCardBack = item.path;
      }
      if (item.type=="businessLicense"){
        showFileUrl.businessLicense = item.path;
      }
      if (item.type=="licenseFront"){
        showFileUrl.licenseFront = item.path;
      }
      if (item.type=="licenseBack"){
        showFileUrl.licenseBack = item.path;
      }
      if (item.type=="certificate"){
        showFileUrl.certificate = item.path;
      }
      if (item.type=="invoice"){
        showFileUrl.invoice = item.path;
      }
      if (item.type=="quotation"){
        showFileUrl.quotation = item.path;
        workorderFileIds.quotationFile = item.fileId;
        fileStore.quotationFile.push({
          id: item.fileId,
          name: item.fileName
        });
      }
      if (item.type=="acceptInsuranceCommercial"){
        showFileUrl.acceptInsuranceCommercial = item.path;
        workorderFileIds.acceptInsuranceCommercialFile = item.fileId;
        fileStore.acceptInsuranceCommercialFile.push({
          id: item.fileId,
          name: item.fileName
        });
      }
      if (item.type=="acceptInsuranceCompulsory"){
        showFileUrl.acceptInsuranceCompulsory = item.path;
        workorderFileIds.acceptInsuranceCompulsoryFile = item.fileId;
        fileStore.acceptInsuranceCompulsoryFile.push({
          id: item.fileId,
          name: item.fileName
        });
      }
      if (item.type=="acceptInsuranceOther"){
        showFileUrl.acceptInsuranceOther.push(item.path);
        workorderFileIds.acceptInsuranceOtherFile.push(item.fileId);
        fileStore.acceptInsuranceOtherFile.push({
          id: item.fileId,
          name: item.fileName
        });
      }
    });
  }

  info.insurance = { 
    type1: [],
    type2: [],
    type3: []
  };

  /*
   * 每次回填都完整重算八个费用维度，避免同一组件刷新其他工单后沿用上一次的切换状态。
   * 后端比例字段是统一首选契约；只有历史数据明确保存了金额而未保存比例时才进入金额模式。
   */
  showFlag.upstream_commercial = shouldUseAmountMode(
    oriInfo.value.upstreamCommercialAmount,
    oriInfo.value.upstreamCommercialPercentage
  );
  showFlag.upstream_compulsory = shouldUseAmountMode(
    oriInfo.value.upstreamCompulsoryAmount,
    oriInfo.value.upstreamCompulsoryPercentage
  );
  showFlag.upstream_tax = shouldUseAmountMode(
    oriInfo.value.upstreamVehicleAndVesselTaxAmount,
    oriInfo.value.upstreamVehicleAndVesselTaxPercentage
  );
  showFlag.downstream_commercial = shouldUseAmountMode(
    oriInfo.value.downstreamCommercialAmount,
    oriInfo.value.downstreamCommercialPercentage
  );
  showFlag.downstream_compulsory = shouldUseAmountMode(
    oriInfo.value.downstreamCompulsoryAmount,
    oriInfo.value.downstreamCompulsoryPercentage
  );
  showFlag.downstream_tax = shouldUseAmountMode(
    oriInfo.value.downstreamVehicleAndVesselTaxAmount,
    oriInfo.value.downstreamVehicleAndVesselTaxPercentage
  );
  showFlag.upstream_non_motor = shouldUseAmountMode(
    oriInfo.value.upstreamNonMotorAmount,
    oriInfo.value.upstreamNonMotorPercentage
  );
  showFlag.downstream_non_motor = shouldUseAmountMode(
    oriInfo.value.downstreamNonMotorAmount,
    oriInfo.value.downstreamNonMotorPercentage
  );

  if (oriInfo.value.nonMotorAmount != null){
    showFlag.is_have_non_motor = true;
  }

  let noSelectValue = ["不投保", "不需要"];
  if (oriInfo.value.workorderInsuranceList != null && oriInfo.value.workorderInsuranceList != undefined){
    oriInfo.value.workorderInsuranceList.forEach(item => { 
      let found = false;

      const type1Option = insuranceOptions.value.type1.find(option => option.id == item.insuranceId);
      if (type1Option) {
        if(!noSelectValue.includes(item.optionJson)){
          info.insurance.type1.push({
          name: type1Option.name,
          value: item.optionJson,
        });
        }
        found = true;
      }
      
      if (!found) {
        const type2Option = insuranceOptions.value.type2.find(option => option.id == item.insuranceId);
        if (type2Option) {
          if(!noSelectValue.includes(item.optionJson)){
            info.insurance.type2.push({
              name: type2Option.name,
              value: item.optionJson,
            });
          }
          found = true;
        }
      }
      
      if (!found) {
        const type3Option = insuranceOptions.value.type3.find(option => option.id == item.insuranceId);
        if (type3Option) {
          if(!noSelectValue.includes(item.optionJson)){
            info.insurance.type3.push({
              name: type3Option.name,
              value: item.optionJson,
            });
          }
          found = true;
        }
      }
    });
  }
  
  if(info.insurance.type1.length == 0){
    showFlag.insuranceType1 = false;
  }
  else {
    showFlag.insuranceType1 = true;
  }

  if(info.insurance.type3.length == 0){
    showFlag.insuranceType3 = false;
  }
  else {
    showFlag.insuranceType3 = true;
  }
}

/**

 * * 查询当前工单完整详情，并为可能缺失的三类车辆证件对象提供空对象，保证模板读取嵌套字段安全。

 */
const getDataById = async () => {
  let res = await selectWorkorderById(id);
  res = res.data;
  if(res.code == 200){
    const data = res.data;
    oriInfo.value = {
      ...data,
      vehicleLicense: data.vehicleLicense || {},
      vehicleCertificate: data.vehicleCertificate || {},
      vehicleInvoice: data.vehicleInvoice || {}
    };
  }
}

/**

 * * 切换非车险报价区域是否参与当前工单报价和上下游金额计算。

 */
const handleNonMotorChange = (flag) => {
  showFlag.is_have_non_motor = flag;
}

/**
 * 判断单项上下游费用是否应使用固定金额输入。比例是新建和空数据的统一默认模式；仅当后端存在
 * 明确金额且比例确实缺失时，才恢复历史金额模式。金额为 0 仍视为明确保存的数据，避免篡改历史选择。
 *
 * @param {*} amount 后端返回的固定金额
 * @param {*} percentage 后端返回的费用比例
 * @returns {boolean} true 表示按金额，false 表示按比例
 */
const shouldUseAmountMode = (amount, percentage) => (
  isEmptyDetailValue(percentage) && !isEmptyDetailValue(amount)
);

/**

 * * 按上下游及险种维度切换“直接金额”与“百分比计算”输入模式，其他维度状态保持不变。

 */
const handleAmountShowChange = (type, flag) => {
  if (type == 'upstreamCommercial'){
    showFlag.upstream_commercial = flag;
  }
  if (type == 'upstreamCompulsory'){
    showFlag.upstream_compulsory = flag;
  }
  if (type == 'upstreamTax'){
    showFlag.upstream_tax = flag;
  }
  if (type == 'upstreamNonMotor'){
    showFlag.upstream_non_motor = flag;
  }
  if (type == 'downstreamCommercial'){
    showFlag.downstream_commercial = flag;
  }
  if (type == 'downstreamCompulsory'){
    showFlag.downstream_compulsory = flag;
  }
  if (type == 'downstreamTax'){
    showFlag.downstream_tax = flag;
  }
  if (type == 'downstreamNonMotor'){
    showFlag.downstream_non_motor = flag;
  }
}

/**

 * * 汇总后端原始工单的四类保费金额；没有任何历史金额时返回空串表示尚未报价。

 */
const computeSumAmount = () => {
  let sum = 0;
  let flag = 0;
  if(oriInfo.value.commercialAmount != null && oriInfo.value.commercialAmount != undefined){
    sum += oriInfo.value.commercialAmount;
    flag++;
  }
  if(oriInfo.value.compulsoryAmount != null && oriInfo.value.compulsoryAmount != undefined){
    sum += oriInfo.value.compulsoryAmount;
    flag++;
  }
  if(oriInfo.value.vehicleAndTaxAmount != null && oriInfo.value.vehicleAndTaxAmount != undefined){
    sum += oriInfo.value.vehicleAndTaxAmount;
    flag++;
  }
  if(oriInfo.value.nonMotorAmount != null && oriInfo.value.nonMotorAmount != undefined){
    sum += oriInfo.value.nonMotorAmount;
    flag++;
  }
  if (flag==0){
    return "";
  }
  return sum;
}

/**

 * * 汇总历史上游商业险、交强险、车船税和非车险结算金额，无数据时保留空值语义。

 */
const computeUpstreamAmount = () => {
  let sum = 0;
  let flag = 0;
  if(oriInfo.value.upstreamCommercialAmount != null && oriInfo.value.upstreamCommercialAmount != undefined){
    sum += oriInfo.value.upstreamCommercialAmount;
    flag++;
  }
  if(oriInfo.value.upstreamCompulsoryAmount != null && oriInfo.value.upstreamCompulsoryAmount != undefined){
    sum += oriInfo.value.upstreamCompulsoryAmount;
    flag++;
  }
  if(oriInfo.value.upstreamVehicleAndVesselTaxAmount != null && oriInfo.value.upstreamVehicleAndVesselTaxAmount != undefined){
    sum += oriInfo.value.upstreamVehicleAndVesselTaxAmount;
    flag++;
  }
  if(oriInfo.value.upstreamNonMotorAmount != null && oriInfo.value.upstreamNonMotorAmount != undefined){
    sum += oriInfo.value.upstreamNonMotorAmount;
    flag++;
  }
  if (flag==0){
    return "";
  }
  return sum; 
}

/**

 * * 汇总历史下游四类业务金额，用于详情的下游结算合计展示。

 */
const computeDownstreamAmount = () => {
  let sum = 0;
  let flag = 0;
  if(oriInfo.value.downstreamCommercialAmount != null && oriInfo.value.downstreamCommercialAmount != undefined){
    sum += oriInfo.value.downstreamCommercialAmount;
    flag++;
  }
  if(oriInfo.value.downstreamCompulsoryAmount != null && oriInfo.value.downstreamCompulsoryAmount != undefined){
    sum += oriInfo.value.downstreamCompulsoryAmount;
    flag++;
  }
  if(oriInfo.value.downstreamVehicleAndVesselTaxAmount != null && oriInfo.value.downstreamVehicleAndVesselTaxAmount != undefined){
    sum += oriInfo.value.downstreamVehicleAndVesselTaxAmount;
    flag++;
  }
  if(oriInfo.value.downstreamNonMotorAmount != null && oriInfo.value.downstreamNonMotorAmount != undefined){
    sum += oriInfo.value.downstreamNonMotorAmount;
    flag++;
  }
  if (flag==0){
    return "";
  }
  return sum;  
}

/**
 * 提交流程节点前等待报价单、商业保单、交强保单及其他附件全部完成上传。
 * 每 200ms 检查一次，30 秒超时后阻止状态推进，避免工单引用尚未生成的文件 ID。
 */
const waitFileUpload = async () => {
  return new Promise((resolve, reject) => {
    const checkupInterval = setInterval(() => {
      if(!loadingFlag.quotation && !loadingFlag.commercial && !loadingFlag.compulsory && loadingFlag.other==0){
        clearInterval(checkupInterval);
        resolve(true);
      }
    }, 200);
    setTimeout(() => {
      clearInterval(checkupInterval);
      reject('上传文件超时');
    }, 30000);
  })
}

/**
 * 校验当前流程表单并等待附件上传，再按操作类型分派到报价、核保、支付、承保成功或失败更新。
 * 承保成功后返回工单列表；任何上传超时都保持原工单状态并提示用户重试。
 */
const handleSubmit = async (formEl, type) => {
  formEl.validate(async (valid) => {
    if (valid) {
      Loading.open();
      try {
        await waitFileUpload();
      } catch(e) {
        Message.error('文件上传超时，请稍后再试');
        Loading.close();
        return;
      }

      if (type == 'quotation'){
        let data = buildQuotationUpdateFrom(8);
        handleQuotationUpdate(data);
      }
      if (type == 'quotationFailed'){
        handleNoCascadeUpdate(buildQuotationFailedFrom());
      }
      if (type == 'underwriting'){
        let data = buildQuotationUpdateFrom(6);
        handleQuotationUpdate(data);
      }
      if (type == 'underwritingFailed'){
        handleNoCascadeUpdate(buildUnderwritingFailedForm());
      }
      if (type == 'pay'){
        handleNoCascadeUpdate(buildPayForm());
      }
      if (type == 'payFailed'){
        handleNoCascadeUpdate(buildPayFailedForm());
      }
      if (type == 'acceptInsurance'){
        await handleAcceptInsuranceUpdate(buildAcceptInsuranceFrom());
        // 承保成功跳转回工单页面继续录单
        router.push('/home/allWorkorder');
      }
      if (type == 'acceptInsuranceFailed'){
        handleNoCascadeUpdate(buildAcceptInsuranceFailedForm());
      }
    }
  });
}

/**

 * * 放弃当前流程表单的未保存修改，使用最近一次后端详情重新构建页面。

 */
const handleResetForm = () => {
  buildInfo();
}

/**

 * * 为流程附件提供预览地址，优先复用已有 URL，本地文件则创建临时 Object URL。

 */
const ensureUploadPreviewUrl = (uploadFile) => {
  if (uploadFile.url != null && uploadFile.url !== '') {
    return uploadFile.url;
  }
  if (uploadFile.raw) {
    uploadFile.url = URL.createObjectURL(uploadFile.raw);
    return uploadFile.url;
  }
  return '';
}

/**

 * * 合并上传组件条目与系统文件记录，使附件同时具备后端文件 ID、名称和本地预览能力。

 */
const buildUploadFileItem = (uploadFile, fileInfo) => {
  return {
    ...uploadFile,
    id: fileInfo?.id,
    name: fileInfo?.fileName || uploadFile.name,
    url: ensureUploadPreviewUrl(uploadFile)
  };
}

/**

 * * 校验原始 File 后执行 OSS 直传，并要求返回可持久化关联的系统文件信息。

 */
const handleSingleFileUpload = async (uploadFile) => {
  const rawFile = uploadFile?.raw;
  if (!rawFile) {
    throw new Error('未获取到待上传文件');
  }
  const { fileInfo } = await uploadToOss(rawFile);
  if (!fileInfo) {
    throw new Error('上传成功但未返回文件信息');
  }
  return {
    fileInfo,
    uploadFileItem: buildUploadFileItem(uploadFile, fileInfo)
  };
}

/**

 * * 单文件上传失败或移除时，同时清空组件列表和请求体文件 ID，并按需显示错误信息。

 */
const clearSingleUploadState = (listKey, fileIdKey, message) => {
  fileStore[listKey] = [];
  workorderFileIds[fileIdKey] = undefined;
  if (message) {
    Message.error(message);
  }
}

/**

 * * 多附件中某一文件上传失败时仅按 uid 移除该项，不影响已经成功上传的其他承保附件。

 */
const removeUploadFileByUid = (listKey, uploadFile, message) => {
  fileStore[listKey] = fileStore[listKey].filter(item => item.uid !== uploadFile.uid);
  if (message) {
    Message.error(message);
  }
}

/**

 * * 校验并上传唯一报价单附件，成功后保存报价文件 ID；失败时回滚整个报价单上传状态。

 */
const handleQuotationChange = async (uploadFile, uploadFiles) => {
  const validFiles = validFileSize(uploadFiles);
  fileStore.quotationFile = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.quotation = true;
    const { fileInfo, uploadFileItem } = await handleSingleFileUpload(uploadFile);
    workorderFileIds.quotationFile = fileInfo.id;
    fileStore.quotationFile = [uploadFileItem];
  }
  catch(error){
    clearSingleUploadState('quotationFile', 'quotationFile', error.message || '文件上传失败，请重试');
  }
  finally{
    loadingFlag.quotation = false;
  }

}

/**

 * * 上传唯一商业险保单并记录系统文件 ID，供承保完成请求建立附件关系。

 */
const handleAcceptInsuranceCommercialChange = async (uploadFile, uploadFiles) => {
  const validFiles = validFileSize(uploadFiles);
  fileStore.acceptInsuranceCommercialFile = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.commercial = true;
    const { fileInfo, uploadFileItem } = await handleSingleFileUpload(uploadFile);
    workorderFileIds.acceptInsuranceCommercialFile = fileInfo.id;
    fileStore.acceptInsuranceCommercialFile = [uploadFileItem];
  }
  catch(error){
    clearSingleUploadState('acceptInsuranceCommercialFile', 'acceptInsuranceCommercialFile', error.message || '文件上传失败，请重试');
  }
  finally{
    loadingFlag.commercial = false;
  }
}

/**

 * * 上传唯一交强险保单并同步预览项和承保请求中的文件 ID。

 */
const handleAcceptInsuranceCompulsoryChange = async (uploadFile, uploadFiles) => {
  const validFiles = validFileSize(uploadFiles);
  fileStore.acceptInsuranceCompulsoryFile = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.compulsory = true;
    const { fileInfo, uploadFileItem } = await handleSingleFileUpload(uploadFile);
    workorderFileIds.acceptInsuranceCompulsoryFile = fileInfo.id;
    fileStore.acceptInsuranceCompulsoryFile = [uploadFileItem];
  }
  catch(error){
    clearSingleUploadState('acceptInsuranceCompulsoryFile', 'acceptInsuranceCompulsoryFile', error.message || '文件上传失败，请重试');
  }
  finally{
    loadingFlag.compulsory = false;
  }
}

/**
 * 并发上传可多选的其他承保附件，按 uid 用上传成功项替换本地占位，并累加对应文件 ID。
 * 任一文件超限或失败只移除该项，loadingFlag.other 以计数方式支持多个上传同时进行。
 */
const handleAcceptInsuranceOtherChange = async (uploadFile, uploadFiles) => {
  let oriLength = uploadFiles.length;
  const validFiles = validFileSize(uploadFiles);
  fileStore.acceptInsuranceOtherFile = validFiles;
  if (validFiles.length != oriLength){
    return;
  }
  try{
    loadingFlag.other++;
    const { fileInfo, uploadFileItem } = await handleSingleFileUpload(uploadFile);
    workorderFileIds.acceptInsuranceOtherFile.push(fileInfo.id);
    const fileIndex = fileStore.acceptInsuranceOtherFile.findIndex(item => item.uid === uploadFile.uid);
    if (fileIndex >= 0) {
      fileStore.acceptInsuranceOtherFile.splice(fileIndex, 1, uploadFileItem);
    }
    uploadFile.id = fileInfo.id;
  }
  catch(error){
    removeUploadFileByUid('acceptInsuranceOtherFile', uploadFile, error.message || '文件上传失败，请重试');
  }
  finally{
    loadingFlag.other--;
  }
}

/**

 * * 移除其他承保附件时同步更新界面文件列表和待提交文件 ID 数组。

 */
const handleAcceptInsuranceOtherRemove = (file, files) => {
  fileStore.acceptInsuranceOtherFile = files;
  workorderFileIds.acceptInsuranceOtherFile = workorderFileIds.acceptInsuranceOtherFile.filter(item => item != file.id);
}

/**

 * * 记录当前正在编辑的流程节点，控制页面展示与该阶段匹配的表单和提交按钮。

 */
const handleFormStatusChange = (status) => {
  formStatus.value = status;
}

/**

 * * 独立切换报价资料、报价结果、支付和承保四个详情区块，避免一个区块影响其他展开状态。

 */
const handleTableExpand = (type) => {
  if (type == 'quotationInfo'){
    showFlag.quotationInfo = !showFlag.quotationInfo;
  }
  if (type == 'quotationResult'){
    showFlag.quotationResult = !showFlag.quotationResult;
  }
  if (type == 'payment'){
    showFlag.payment = !showFlag.payment;
  }
  if (type == 'acceptInsurance'){
    showFlag.acceptInsurance = !showFlag.acceptInsurance;
  }
}

/**

 * * 将结算比例和固定金额组合为“比例%/¥金额”文本，缺失部分仍保留统一货币格式。

 */
const computePercentageAndAmountStr = (percentage, amount) => {
  let result = "";
  if(percentage != null && percentage !=undefined){
    result += `${percentage}%/`;
  }
  result += '¥';
  if(amount != null && amount !=undefined){
    result += `${amount}`;
  }
  return result;
}

/**

 * * 根据附件业务类型下载报价单或承保保单；其他承保附件会逐项触发下载。

 */
const handleDownload = (type) => {
  if(type=="quotation"){
    downloadByUrl(showFileUrl.quotation);
  }
  if(type=="acceptInsuranceCommercial"){
    downloadByUrl(showFileUrl.acceptInsuranceCommercial)
  }
  if(type=="acceptInsuranceCompulsory"){
    downloadByUrl(showFileUrl.acceptInsuranceCompulsory)
  }
  if(type=="acceptInsuranceOther"){
    showFileUrl.acceptInsuranceOther.forEach(item => {
      downloadByUrl(item);
    })
  }
}

/**
 * 按保费和比例计算结算金额；类型 1 先按 1.06 还原不含税金额，再计算百分比。
 * 非数字输入返回短横线，正常结果固定保留两位小数。
 */
const percentageMulti = (percentage, amount, type) => {
  percentage = Number(percentage);
  amount = Number(amount);
  if ( isNaN(percentage) || isNaN(amount)){
    return '-';
  }
  if (type == 1){
    amount = amount / 1.06;
  }
  return (amount * percentage / 100.0).toFixed(2);
}

/**
 * 构建报价或核保成功阶段的更新数据。
 * 该方法转换保险起期和保费，按每个上下游维度选择提交固定金额或百分比，按需加入非车险及报价单，
 * 并保证编辑较早阶段资料时不会把已经推进到更后状态的工单倒退。
 */
const buildQuotationUpdateFrom = (status) => {
  let data = {};
  data.id = oriInfo.value.id;
  data.quotationNo = info.quotationNo;
  data.commercialInsuranceStartTime = convertDateToSecondTimestamp(info.commercialInsuranceStartTime);
  data.compulsoryInsuranceStartTime = convertDateToSecondTimestamp(info.compulsoryInsuranceStartTime);
  data.commercialAmount = Number(info.commercialAmount);
  data.compulsoryAmount = Number(info.compulsoryAmount);
  data.vehicleAndTaxAmount = Number(info.vehicleAndTaxAmount);
  data.upstreamComputeType = info.upstreamComputeType;
  data.downstreamComputeType = info.downstreamComputeType;
  // 修改信息的时候不覆盖当前状态
  if(oriInfo.value.status > status){
    data.status = oriInfo.value.status;
  }
  else {
    data.status = status;
  }
  data.quotationRemark = info.quotationRemark;
  // if (status == 4){
  //   data.quotationRemark = info.quotationRemark;
  // }
  // else {
  //   data.underwritingRemark = info.underwritingRemark;
  // }
  if(showFlag.upstream_commercial){
    data.upstreamCommercialAmount = Number(info.upstreamCommercialAmount);
  }
  else {
    data.upstreamCommercialPercentage = Number(info.upstreamCommercialPercentage);
  }
  if(showFlag.upstream_compulsory){
    data.upstreamCompulsoryAmount = Number(info.upstreamCompulsoryAmount);
  }
  else {
    data.upstreamCompulsoryPercentage = Number(info.upstreamCompulsoryPercentage);
  }
  if(showFlag.upstream_tax){
    data.upstreamVehicleAndVesselTaxAmount = Number(info.upstreamVehicleAndVesselTaxAmount);
  }
  else {
    data.upstreamVehicleAndVesselTaxPercentage = Number(info.upstreamVehicleAndVesselTaxPercentage);
  }

  if (showFlag.downstream_commercial){
    data.downstreamCommercialAmount = Number(info.downstreamCommercialAmount); 
  }
  else {
    data.downstreamCommercialPercentage = Number(info.downstreamCommercialPercentage);
  }
  if (showFlag.downstream_compulsory){
    data.downstreamCompulsoryAmount = Number(info.downstreamCompulsoryAmount); 
  }
  else {
    data.downstreamCompulsoryPercentage = Number(info.downstreamCompulsoryPercentage);
  }
  if (showFlag.downstream_tax){
    data.downstreamVehicleAndVesselTaxAmount = Number(info.downstreamVehicleAndVesselTaxAmount); 
  }
  else {
    data.downstreamVehicleAndVesselTaxPercentage = Number(info.downstreamVehicleAndVesselTaxPercentage);
  }

  if(showFlag.is_have_non_motor){
    data.nonMotorInsuranceName = info.nonMotorInsuranceName;
    data.nonMotorCoverageAmount = Number(info.nonMotorCoverageAmount);
    data.nonMotorAmount = Number(info.nonMotorAmount);
    if (showFlag.upstream_non_motor){
      data.upstreamNonMotorAmount = Number(info.upstreamNonMotorAmount);
    }
    else {
      data.upstreamNonMotorPercentage = Number(info.upstreamNonMotorPercentage);
    }
    if (showFlag.downstream_non_motor){
      data.downstreamNonMotorAmount = Number(info.downstreamNonMotorAmount);
    }
    else {
      data.downstreamNonMotorPercentage = Number(info.downstreamNonMotorPercentage);
    }
  }
  
  data.workorderFileList = [];
  if (workorderFileIds.quotationFile != null && workorderFileIds.quotationFile != undefined){
    data.workorderFileList.push({
      fileId: workorderFileIds.quotationFile,
      type: 'quotation'
    });
  }
  // console.log(data);
  return data;
}

/**

 * * 构建报价失败的最小状态变更，只提交工单 ID、失败原因和状态 3，不覆盖已有报价资料。

 */
const buildQuotationFailedFrom = () => {
  let data = {};
  data.id = oriInfo.value.id;
  data.quotationFailedRemark = info.quotationFailedRemark; 
  data.status = 3;
  return data;
}

/**

 * * 构建核保失败更新，将失败说明写入专属字段并把工单推进到状态 5。

 */
const buildUnderwritingFailedForm = () => { 
  let data = {};
  data.id = oriInfo.value.id;
  data.underwritingFailedRemark = info.underwritingFailedRemark;
  data.status = 5;
  console.log(data);
  return data;
}

/**

 * * 构建支付确认成功数据，包含应付金额、付款人及银行资料，并将工单推进到待承保状态 8。

 */
const buildPayForm = () => {
  let data = {};
  data.id = oriInfo.value.id;
  data.requiredPayAmount = Number(info.requiredPayAmount);
  data.payName = info.payName;
  data.payIdNum = info.payIdNum;
  data.payBank = info.payBank;
  data.payBankCardNum = info.payBankCardNum;
  data.payRemark = info.payRemark;
  data.status = 8;
  return data;
}

/**

 * * 构建支付失败的最小更新，只保存失败说明并标记状态 7。

 */
const buildPayFailedForm = () => { 
  let data = {};
  data.id = oriInfo.value.id;
  data.payFailedRemark = info.payFailedRemark;
  data.status = 7;
  return data;
}

/**
 * 构建承保完成数据，记录当前完成时间、保单号、物流和付款资料，并关联商业险、交强险及其他附件。
 * 其他附件逐个转换为统一的工单文件关系，最终状态固定为 10。
 */
const buildAcceptInsuranceFrom = () => { 
  let data = {};
  data.finishTime = convertDateToSecondTimestamp(new Date());
  data.id = oriInfo.value.id;
  data.payName = info.payName;
  data.payIdNum = info.payIdNum;
  data.payBank = info.payBank;
  data.payBankCardNum = info.payBankCardNum;
  data.commercialPolicyNo = info.commercialPolicyNo;
  data.compulsoryPolicyNo = info.compulsoryPolicyNo;
  data.trackingNum = info.trackingNum;
  data.logisticsCompany = info.logisticsCompany;
  data.acceptInsuranceRemark = info.acceptInsuranceRemark;
  data.status = 10;
  data.workorderFileList = [];
  if (workorderFileIds.acceptInsuranceCommercialFile != null && workorderFileIds.acceptInsuranceCommercialFile != undefined){
    data.workorderFileList.push({
      fileId: workorderFileIds.acceptInsuranceCommercialFile,
      type: 'acceptInsuranceCommercial'
    });
  }
  if (workorderFileIds.acceptInsuranceCompulsoryFile != null && workorderFileIds.acceptInsuranceCompulsoryFile != undefined){
    data.workorderFileList.push({
      fileId: workorderFileIds.acceptInsuranceCompulsoryFile,
      type: 'acceptInsuranceCompulsory'
    });
  }
  if (workorderFileIds.acceptInsuranceOtherFile != null && workorderFileIds.acceptInsuranceOtherFile != undefined && workorderFileIds.acceptInsuranceOtherFile.length > 0){
    workorderFileIds.acceptInsuranceOtherFile.forEach(fileId => {
      data.workorderFileList.push({
        fileId: fileId,
        type: 'acceptInsuranceOther'
      });
    });
  }
  console.log(data);
  return data;
}

/**

 * * 构建承保失败更新，保留已录入资料，仅写入失败原因和状态 9。

 */
const buildAcceptInsuranceFailedForm = () => { 
  let data = {};
  data.id = oriInfo.value.id;
  data.acceptInsuranceFailedRemark = info.acceptInsuranceFailedRemark;
  data.status = 9;
  return data;
}

/**

 * * 持久化报价或核保成功数据；后端成功后重新加载完整详情，以真实状态和计算结果覆盖本地表单。

 */
const handleQuotationUpdate = async (data) => {
  try{
    await updateQuotation(data).then(res=>{
      res = res.data;
      if(res.code == 200){
        Message.success("反馈成功");
        buildPage();
      }
    });
  }
  finally{
    Loading.close();
  }
}

/**

 * * 保存无需重建险种或文件关系的失败、支付等小范围状态更新，成功后重载详情。

 */
const handleNoCascadeUpdate = async (data) => {
  try{
    await updateNoCascade(data).then(res=>{
      res = res.data;
      if(res.code == 200){
        Message.success("反馈成功");
        buildPage();
      }
    });
  }
  finally{
    Loading.close();
  }
}

/**

 * * 持久化承保完成资料及其附件关系，成功后刷新工单详情；调用方随后可返回工单列表。

 */
const handleAcceptInsuranceUpdate = async (data) => {
  try{
    await updateAcceptInsurance(data).then(res=>{
      res = res.data;
      if(res.code == 200){
        Message.success("反馈成功");
        buildPage();
      }
    });
  }
  finally{
    Loading.close();
  }
}

/**

 * * 打开指定阶段的编辑区域：状态 0 使用基础资料抽屉，其余状态显示流程表单并平滑滚动到编辑位置。

 */
const handleEdit = async (status) => {
  if(status == 0){
    drawer.value = true;
    return;
  }
  showFlag.uploadForm = true;
  formStatus.value = status;
  await nextTick();
  scrollRef.value.scrollIntoView({
    behavior: 'smooth',
    block: 'start',     
    inline: 'nearest'  
  });
}

/**

 * * 基础资料抽屉保存后重新加载险种和工单详情，使主详情页同步最新数据。

 */
const handleRefresh = () => {
  buildPage();
}
</script>


<style scoped>
.workorder_head_container {
  display: flex;
  justify-content: left;
  align-items: center;
  width: 100%;
  background-color: #fff;
  border: 1px solid var(--insurance-border);
  border-radius: var(--insurance-radius);
  box-shadow: var(--insurance-shadow);
  min-height: 120px;
  margin-bottom: 15px;
}

.workorder_head_left {
  width: 120px;
  height: 100%;
  padding: 10px;
  /* background-color: #F56C6C; */
}

.workorder_head_right {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 12px;
  flex: 1;
  height: 100%;
}

.workorder_head_right_item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px; 
}

.head_align_item {
  width: calc((100% - 30px) / 4);
  text-align: left;
}

.form_text {
  color: var(--insurance-secondary);
}

.step_container {
  padding: 10px;
  width: 100%;
  display: flex;
  justify-content: center;
}

.detail_info_container {
  width: 100%;
  border: 1px solid var(--insurance-border);
  border-radius: var(--insurance-radius);
  overflow: hidden;
}
.detail_head {
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  height: 50px;
  background-color: var(--insurance-surface-soft);
  border-bottom: 1px solid var(--insurance-border);
}
.detail_content {
  width: 100%;
  padding: 10px;
  font-size: 14px;
  border-bottom: 1px solid var(--insurance-border);
  min-height: 50px;
}

.workorder_file_title {
  font-size: 14px;
  text-align: left;
  margin: 10px 0;
}

.detail_head_item {
  display: flex;
  align-items: center;
  height: 100%;
}
.content_row {
  padding: 10px;
  display: flex;
  justify-content: left;
  gap: 15px;
  align-items: center;
  flex-wrap: wrap;
}
.img_detail_show {
  display: flex;
  justify-content: left;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 20px;
}
.img_detail_show_left {
  display: flex;
  gap: 15px;
  justify-content: space-between;
  align-items: center;
  margin-right: 20px;
  white-space: nowrap;
}

/**
 * 证件预览和未上传占位共用固定尺寸，避免异步文件数据到达时引起详情布局跳动。
 * 空状态使用低强调度的虚线边框、文档图标和明确文案，与真实图片加载错误保持语义区分。
 */
.document-preview,
.document-empty {
  width: 124px;
  height: 124px;
  border-radius: 6px;
  box-sizing: border-box;
}

.document-preview {
  border: 1px solid #ebeef5;
  background-color: #f8fafc;
}

.document-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed #cbd5e1;
  background-color: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
}

.document-empty__icon {
  width: 28px;
  height: 28px;
  color: #94a3b8;
}

.document-empty__icon :deep(svg) {
  width: 100%;
  height: 100%;
}
.img_detail_show_right {
  display: flex;
  justify-content: left;
  flex-wrap: wrap;
  gap: 5px;
}

.workorder_detail_form_title {
  margin-right: 20px;
}

.form_gap {
  margin-right: 30px;
}

.detail-value {
  min-width: 16px;
  color: var(--insurance-text);
}

.detail-value--empty {
  color: #a8abb2;
  font-weight: 400;
}

.form_margin {
  margin-top: 20px;
}

.workorder_detail_insurance_container {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.workorder_detail_insurance_table {
  display: flex;
  flex-direction: column;
  width: calc((100% - 20px) / 3);
  /* height: 100%; */
  text-align: left;
}
.workorder_detail_insurance_table_content{
  flex: 1;
  padding: 10px;
  background-color: var(--insurance-surface-soft);
}

.workorder_detail_insurance_table_item {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  margin-bottom: 2px;
}

.workorder_form_button_container {
  display: flex;
  justify-content: left;
  gap: 30px;
  align-items: center;
}

.workoerder_form_button {
  min-width: 150px;
  height: 60px;
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px solid #DCDFE6;
  border-radius: 4px;
}

.workoerder_form_button_select {
  min-width: 150px;
  height: 60px;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 4px;
  background-color: var(--insurance-primary);
  color: #fff;
}
.workoerder_form_big_title {
  font-size: 15px;
  padding: 16px 0;
}

.file_download_text {
  color: var(--insurance-primary);
}

</style>
