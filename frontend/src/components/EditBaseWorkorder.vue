<template>
  <div class="container_body card">
    <div class="title_container">
      <div class="search_title" style="margin: 0;">基础信息</div>
      <div class="title_radio_container">
        <el-radio-group v-model="info.type">
          <el-radio :value="1">新车</el-radio>
          <el-radio :value="0">旧车</el-radio>
        </el-radio-group>
      </div>
    </div>
      <el-form
        ref="editRef"
        style="max-width: 700px; margin: 20px auto;"
        :model="info"
        status-icon
        :rules="rules"
        label-width="auto"
        class="login_form"
        label-position="left"
      >
        <el-form-item prop="createMerchantId" label="提交店铺">
          <el-select
            style="width: 100%;"
            v-model="info.createMerchantId"
            filterable
            clearable
            remote
            reserve-keyword
            placeholder="输入关键词选择提交店铺"
            :remote-method="getCreateMerchantOption"
            :loading="loading.createMerchant"
          >
            <el-option
              v-for="item in createMerchantOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item prop="createBy" label="提交人">
          <el-select
            style="width: 100%;"
            v-model="info.createBy"
            clearable
            placeholder="请选择提交人"
            :loading="loading.createBy"
          >
            <el-option
              v-for="item in createByOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item prop="areaCode" label="业务区域">
          <!-- <el-cascader
            style="width: 100%;"
            v-model="info.areaCode" 
            :options="areaOptions" 
            clearable 
            placeholder="请选择业务区域"
          /> -->
          <AreaSelect v-model="info.areaCode" placeholder="请选择业务区域" :multi="false" style="width: 100%;"></AreaSelect>
        </el-form-item>
        <el-form-item prop="ownerPhone" label="车主电话">
          <el-input
          v-model="info.ownerPhone"
          class="responsive-input"
          placeholder="请输入车主电话"
          />
        </el-form-item>
        <el-form-item label="上传用户信息">
          <el-radio-group v-model="info.ownerType">
            <el-radio :value="0">上传身份证</el-radio>
            <el-radio :value="1">上传营业执照</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="上传身份证" v-show="info.ownerType == 0">
          <el-upload
          v-loading="loadingFlag.idCardBack" 
          drag
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.idCardBack"
          :on-change="handleIdCardBackChange"
          :class="file.idCardBack.length>0?'disabled':''"
          style="margin-right: 40px;">
            <div>
              <div>上传头像面</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'idCardBack')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'idCardBack')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'idCardBack')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
          <el-upload
          v-loading="loadingFlag.idCardFront"  
          drag
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.idCardFront"
          :on-change="handleIdCardFrontChange"
          :class="file.idCardFront.length>0?'disabled':''">
            <div>
              <div>上传国徽面</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'idCardFront')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'idCardFront')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'idCardFront')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="身份证识别信息" v-if="showFlag.idCardRecognition && info.ownerType == 0" label-position="top" class="cascade_form_container">
          <el-form
          ref="idCardRef"
          status-icon
          label-position="left"
          :inline="true"
          :inline-message="false"
          class="cascade_form">
            <el-form-item label="姓名">
              <el-input
              v-model="info.ownerName"
              class="responsive-input"
              maxlength="100"
              placeholder="请补充"
              />
            </el-form-item>
            <el-form-item label="证件号码">
              <el-input
              v-model="info.ownerIdNum"
              class="responsive-input"
              placeholder="请补充"
              maxlength="50"
              />
            </el-form-item>
          </el-form>
        </el-form-item>
        <el-form-item label="上传营业执照" v-show="info.ownerType == 1">
          <el-upload
          v-loading="loadingFlag.businessLicense" 
          drag 
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.businessLicense"
          :on-change="handleBusinessLicenseChange"
          :class="file.businessLicense.length>0?'disabled':''"
          style="margin-right: 40px;">
            <div>
              <div>点击上传</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'businessLicense')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'businessLicense')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'businessLicense')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="营业执照识别信息" v-if="showFlag.businessLicenseRecognition && info.ownerType == 1" label-position="top" class="cascade_form_container">
          <el-form
          ref="idCardRef"
          status-icon
          label-position="left"
          :inline="true"
          :inline-message="false"
          class="cascade_form">
            <el-form-item label="单位名称">
              <el-input
              v-model="info.organizationName"
              class="responsive-input"
              maxlength="95"
              placeholder="请补充"
              />
            </el-form-item>
            <el-form-item label="社会信用代码">
              <el-input
              v-model="info.socialCreditCode"
              class="responsive-input"
              placeholder="请补充"
              maxlength="95"
              />
            </el-form-item>
          </el-form>
        </el-form-item>
        <el-form-item label="上传行驶证" v-show="info.type==0">
          <el-upload
          v-loading="loadingFlag.licenseBack" 
          drag 
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.licenseBack"
          :on-change="handleLicenseBackChange"
          :class="file.licenseBack.length>0?'disabled':''"
          style="margin-right: 40px;">
            <div>
              <div>上传副页</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'licenseBack')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'licenseBack')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'licenseBack')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
          <el-upload
          v-loading="loadingFlag.licenseFront" 
          drag 
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.licenseFront"
          :on-change="handleLicenseFrontChange"
          :class="file.licenseFront.length>0?'disabled':''">
            <div>
              <div>上传正页</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'licenseFront')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'licenseFront')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'licenseFront')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="行驶证识别信息" v-if="showFlag.licenseRecognition && info.type==0" label-position="top" class="cascade_form_container">
          <div class="params_container cascade_params_container cascade_form">
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车牌号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.licensePlate" 
                placeholder="请补充"
                maxlength="45"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车辆类型</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.vehicleType" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">所有人</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.ownerName" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">使用性质</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.usageNature" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">厂牌型号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.brandModel" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车架号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.vehicleCode" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发动机号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.engineCode" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">注册日期</span>
              <div class="form_content cascade_form_content">
                <el-date-picker
                  style="width: 100%;"
                  v-model="info.vehicleLicense.registrationDate"
                  type="date"
                  placeholder="请补充"
                />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发证日期</span>
              <div class="form_content cascade_form_content">
                <el-date-picker
                  style="width: 100%;"
                  v-model="info.vehicleLicense.issueDate"
                  type="date"
                  placeholder="请补充"
                />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">核定载客</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.seats" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'licenseSeats')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">座</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">核定载质量</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.approvedLoadCapacity" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'licenseApprovedLoadCapacity')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">kg</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">整备质量</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.curbWeight" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'licenseCurbWeight')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">kg</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">是否过户</span>
              <div class="form_content cascade_form_content">
                <el-radio-group v-model="info.vehicleLicense.isTransfer">
                  <el-radio :value="1" style="margin-right: 10px;">过户</el-radio>
                  <el-radio :value="0">非过户</el-radio>
                </el-radio-group>
              </div>
            </div>
            <div class="param_item cascade_param_item" v-if="info.vehicleLicense.isTransfer==1">
              <span class="form_title cascade_form_title">过户日期</span>
              <div class="form_content cascade_form_content">
                <el-date-picker
                  style="width: 100%;"
                  v-model="info.vehicleLicense.transferDate"
                  type="date"
                  placeholder="请补充"
                />
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="上传合格证" v-show="info.type==1">
          <el-upload
          v-loading="loadingFlag.certificate" 
          drag 
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.certificate"
          :on-change="handleCertificateChange"
          :class="file.certificate.length>0?'disabled':''"
          style="margin-right: 40px;">
            <div>
              <div>点击上传</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'certificate')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'certificate')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'certificate')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="合格证识别信息" v-if="showFlag.certificateRecognition && info.type==1" label-position="top" class="cascade_form_container">
          <div class="params_container cascade_params_container cascade_form">
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">厂牌型号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.brandModel" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车辆类型</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.vehicleType" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车架号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.vehicleCode" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发动机号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.engineCode" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">整备质量</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.curbWeight" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'certificateCurbWeight')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">kg</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">核定载客</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.seats" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'certificateSeats')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">座</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">排量</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.displacement" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'certificateDisplacement')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">ml</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">核定载质量</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.approvedLoadCapacity" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'certificateApprovedLoadCapacity')">
                <template #suffix>
                    <span style="margin-right: 8px; color: #666;">kg</span>
                  </template>
                </el-input>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="上传购车发票" v-show="info.type==1">
          <el-upload
          v-loading="loadingFlag.invoice" 
          drag 
          action="#" 
          list-type="picture-card" 
          :auto-upload="false" 
          :multiple=false
          :file-list="file.invoice"
          :on-change="handleInvoiceChange"
          :class="file.invoice.length>0?'disabled':''"
          style="margin-right: 40px;">
            <div>
              <div>点击上传</div>
              <el-icon><Plus /></el-icon>
            </div>
            <template #file="{ file }">
              <div>
                <img class="el-upload-list__item-thumbnail" :src="file.url" alt="" />
                <span class="el-upload-list__item-actions">
                  <span
                    class="el-upload-list__item-preview"
                    @click="handlePictureCardPreview(file, 'invoice')"
                  >
                    <el-icon><zoom-in /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleDownload(file, 'invoice')"
                  >
                    <el-icon><Download /></el-icon>
                  </span>
                  <span
                    v-if="!disabled"
                    class="el-upload-list__item-delete"
                    @click="handleRemove(file, 'invoice')"
                  >
                    <el-icon><Delete /></el-icon>
                  </span>
                </span>
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="购车发票识别信息" v-if="showFlag.invoiceRecognition && info.type==1" label-position="top" class="cascade_form_container">
          <div class="params_container cascade_params_container cascade_form">
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发票金额</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.invoiceAmount" 
                placeholder="请补充"
                maxlength="15"
                @blur="handleNumberInputChange($event, 'invoiceAmount')">
                <template #suffix>
                    <span style="margin-right: 8px; color: #666;">元</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">购方名称</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.buyerName" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">购方身份证</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.buyerIdNum" 
                placeholder="请补充"
                maxlength="45"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车辆类型</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.vehicleType" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">厂牌型号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.brandModel" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车架号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.vehicleCode" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发动机号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.engineCode" 
                placeholder="请补充"
                maxlength="95"/>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">核定载客</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.seats" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'invoiceSeats')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">座</span>
                  </template>
                </el-input>
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">核定载质量</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.approvedLoadCapacity" 
                placeholder="请补充"
                maxlength="9"
                @blur="handleNumberInputChange($event, 'invoiceApprovedLoadCapacity')">
                  <template #suffix>
                    <span style="margin-right: 8px; color: #666;">kg</span>
                  </template>
                </el-input>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item prop="commercialInsuranceStartTime" label="商业险起保日期">
          <el-date-picker
            style="width: 100%;"
            v-model="info.commercialInsuranceStartTime"
            type="date"
            placeholder="请选择商业险起保日期"
          />
        </el-form-item>
        <el-form-item prop="compulsoryInsuranceStartTime" label="交强险起保日期">
          <el-date-picker
            style="width: 100%;"
            v-model="info.compulsoryInsuranceStartTime"
            type="date"
            placeholder="请选择交强险起保日期"
          />
        </el-form-item>
        <el-form-item style="width: 100%;">
          <div class="table_form_container">
            <div class="table_form_item" style="background-color: #F9F8F8;">
              <div class="table_form_item_column">险种名称</div>
              <div class="table_form_item_column">绝对免赔额</div>
              <div class="table_form_item_column">投保选项</div>
            </div>
            <div class="table_form_item">
              <div class="table_form_item_column table_form_item_title">商业险</div>
              <div class="table_form_item_column"></div>
              <div class="table_form_item_column"></div>
            </div>
            <div class="table_form_item" v-for="item in insuranceOptions.type1.slice(0,4)" :key="item.id">
              <div class="table_form_item_column">{{ item.name }}</div>
              <div class="table_form_item_column" v-if="item.deductibleOptions != null">
                <el-select
                  style="width: 100%;"
                  v-model="item.deductibleValue" 
                  :options="item.deductibleOptions" 
                  placeholder=""
                />
              </div>
              <div class="table_form_item_column">
                <el-select
                  style="width: 100%;"
                  v-model="item.value" 
                  :options="item.options" 
                  placeholder=""
                />
              </div>
            </div>
            <template v-if="showFlag.insuranceVisible">
              <div class="table_form_item" v-for="item in insuranceOptions.type1.slice(4)" :key="item.id">
                <div class="table_form_item_column">{{ item.name }}</div>
                <div class="table_form_item_column" v-if="item.deductibleOptions != null">
                  <el-select
                    style="width: 100%;"
                    v-model="item.deductibleValue" 
                    :options="item.deductibleOptions" 
                    placeholder=""
                  />
                </div>
                <div class="table_form_item_column">
                  <el-select
                    style="width: 100%;"
                    v-model="item.value" 
                    :options="item.options" 
                    placeholder=""
                  />
                </div>
              </div>
            </template>
            <div class="table_form_item" v-if="showFlag.insuranceVisible">
              <div class="table_form_item_column table_form_item_title">增值服务</div>
              <div class="table_form_item_column"></div>
              <div class="table_form_item_column"></div>
            </div>
            <template v-if="showFlag.insuranceVisible">
              <div class="table_form_item" v-for="item in insuranceOptions.type2" :key="item.id">
                <div class="table_form_item_column">{{ item.name }}</div>
                <div class="table_form_item_column" v-if="item.deductibleOptions != null">
                  <el-select
                    style="width: 100%;"
                    v-model="item.deductibleValue" 
                    :options="item.deductibleOptions" 
                    placeholder=""
                  />
                </div>
                <div class="table_form_item_column">
                  <el-select
                    style="width: 100%;"
                    v-model="item.value" 
                    :options="item.options" 
                    placeholder=""
                  />
                </div>
              </div>
            </template>
            <div class="table_form_item" style="justify-content: center;">
              <div style="color: #A8ABB2;" class="no_select" @click="handleExpandInsurance">{{ showFlag.insuranceVisible ? "收起其他附加险":"展开其他附加险" }}</div>
            </div>
            <div class="table_form_item">
              <div class="table_form_item_column table_form_item_title">强制险</div>
              <div class="table_form_item_column"></div>
              <div class="table_form_item_column"></div>
            </div>
            <div class="table_form_item" v-for="item in insuranceOptions.type3" :key="item.id">
              <div class="table_form_item_column">{{ item.name }}</div>
              <div class="table_form_item_column" v-if="item.deductibleOptions != null">
                <el-select
                  style="width: 100%;"
                  v-model="item.deductibleValue" 
                  :options="item.deductibleOptions" 
                  placeholder=""
                />
              </div>
              <div class="table_form_item_column">
                <el-select
                  style="width: 100%;"
                  v-model="item.value" 
                  :options="item.options" 
                  placeholder=""
                />
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item prop="insuranceMerchantId" label="机构名称">
          <el-select
            style="width: 100%;"
            v-model="info.insuranceMerchantId"
            filterable
            clearable
            remote
            reserve-keyword
            placeholder="输入关键词选择机构"
            :loading="loading.insurance"
          >
            <el-option
              v-for="item in insuranceCompanyOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item prop="remark" label="备注">
          <el-input
            v-model="info.remark"
            resize="none"
            style="width: 100%;"
            :rows="4"
            type="textarea"
            placeholder="请输入备注"
            maxlength="400"
            show-word-limit
          />
        </el-form-item>
        <div>
          <el-button type="primary" @click="handleSubmit(editRef)">{{ props.type=='add'?'新增':'更新' }}</el-button>
          <el-button @click="handleReset" v-if="props.type=='update'">重置</el-button>
        </div>
      </el-form>
  </div>

  <el-dialog v-model="showFlag.dialogVisible">
    <img w-full :src="dialogImageUrl" alt="预览图片" style="width: 100%; height: 100%;"/>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, onMounted, watch, defineProps, defineEmits } from 'vue';
import { validatePhoneNumber, validFileSize } from '@/utils/validate';
import { insertWorkorder, selectWorkorderById, updateWorkorderBaseInfo } from '@/api/workorder';
import Message from '@/utils/message';
import { imgRecognition } from '@/api/ocr';
import { upload } from '@/api/file';
import { selectAllInsurance } from '@/api/insurance';
import { jsonStrToObj, transNumStrToNum } from '@/utils/convert';
import { downloadByUrl } from '@/api/file';
import { isNumber } from '@/utils/validate';
import { selectDownstreamOption } from '@/api/downstream';
import { selectUserOptionByMerchantId } from '@/api/user';
// import { selectInusranceCompanyOptions } from '@/api/upstream';
// import { selectAreaByMerhcantId } from '@/api/merchant';
import { getCascadeAreaCode } from '@/utils/ChinaCitys';
import { convertDateToSecondTimestamp } from '@/utils/time';
import { extractNonEmptyProps } from '@/utils/convert';
import AreaSelect from './AreaSelect.vue';
import { selectInsuranceCompanyByAreaCode } from '@/api/merchant';
import Loading from '@/utils/loading';
import { useRouter } from 'vue-router';

const emit = defineEmits(['refresh']);

const router = useRouter();
const loadingFlag = reactive({
  idCardFront: false,
  idCardBack: false,
  licenseFront: false,
  licenseBack: false,
  certificate: false,
  invoice: false,
  businessLicense: false,
});

const refresh = ref(1);
watch(refresh, (newVal, oldVal) => {
  emit('refresh', { newVal, oldVal });
});


const props = defineProps({
  type: {
    type: String,
    required: false,
    default: 'add'
  },
  id: {
    type: [String, Number],
    required: false,
    default: ''
  }
});

let type = 'add';
let id = undefined;
const pageStatus = ref('ready');

const editRef = ref();

const dialogImageUrl = ref("");

const loading = reactive({
  createMerchant: false,
  createBy: false,
  insurance: false,
  area: false
});

const createMerchantOptions = ref([]);

const insuranceCompanyOptions = ref([]);

const workorderFileId = reactive({
  idCardFrontId: undefined,
  idCardBackId: undefined,
  licenseFrontId: undefined,
  licenseBackId: undefined,
  certificateId: undefined,
  invoiceId: undefined,
  businessLicenseId: undefined
});

const insuranceOptions = ref({
  type1: [],
  type2: [],
  type3: []
});

const showFlag = reactive({
  dialogVisible: false,
  insuranceVisible: false,
  idCardRecognition: false,
  licenseRecognition: false,
  certificateRecognition: false,
  invoiceRecognition: false,
  businessLicenseRecognition: false
});

const file = reactive({
  idCardBack: [],
  idCardFront: [],
  certificate: [],
  invoice: [],
  licenseFront: [],
  licenseBack: [],
  businessLicense: []
});

const rules = reactive({
  createMerchantId: [
    { required: true, message: '请选择提交店铺', trigger: 'blur' }
  ],
  createBy: [
    // { required: true, message: '请选择提交人', trigger: 'blur' }
  ],
  areaCode: [
    { required: true, message: '请选择业务区域', trigger: 'blur' }
  ],
  ownerPhone: [
    { required: true, message: '请输入车主电话', trigger: 'blur' },
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
  ],
  commercialInsuranceStartTime: [
    { required: true, message: '请选择商业险起保日期', trigger: 'blur' }
  ],
  compulsoryInsuranceStartTime: [
    { required: true, message: '请选择交强险起保日期', trigger: 'blur' }
  ],
  insuranceMerchantId: [
    { required: true, message: '请选择保险公司', trigger: 'blur' }
  ],
  remark: [
    { min: 1, max: 400, message: '输入内容过长', trigger: 'blur' },
  ]
});

const info = reactive({
  ownerType: 0,
  type: 1,
  createMerchantId: undefined,
  createBy: undefined,
  areaCode: [],
  ownerPhone: "",
  ownerIdNum: "",
  ownerName: "",
  commercialInsuranceStartTime: new Date(),
  compulsoryInsuranceStartTime: new Date(),
  insuranceMerchantId: undefined,
  remark: "",
  vehicleLicense: {
    licensePlate: undefined,
    vehicleType: undefined,
    ownerName: undefined,
    usageNature: undefined,
    brandModel: undefined,
    vehicleCode: undefined,
    engineCode: undefined,
    registrationDate: undefined,
    issueDate: undefined,
    seats: undefined,
    approvedLoadCapacity: undefined,
    curbWeight: undefined,
    isTransfer: 0,
    transferDate: undefined
  },
  vehicleCertificate: {
    brandModel: undefined,
    vehicleType: undefined,
    vehicleCode: undefined,
    engineCode: undefined,
    seats: undefined,
    curbWeight: undefined,
    displacement: undefined,
    approvedLoadCapacity: undefined,
  },
  vehicleInvoice: {
    invoiceAmount: undefined,
    buyerName: undefined,
    buyerIdNum: undefined,
    vehicleType: undefined,
    brandModel: undefined,
    vehicleCode: undefined,
    engineCode: undefined,
    seats: undefined,
    approvedLoadCapacity: undefined,
  }
});

const oriInfo = ref({});

// const areaOptions = ref([]);

const createByOptions = ref([]);

onMounted(async () => {
  try{
    Loading.open();
    type = props.type;
    id = props.id;
    await Promise.all([
      getAllInsuranceOption(),
      type == 'update' ? getDataById() : Promise.resolve()
    ]);
    if (type == 'update') {
      buildInfo();
    }
  }
  finally{
    Loading.close();
  }
});

const reset = () => {
  workorderFileId.idCardFrontId = undefined;
  workorderFileId.idCardBackId = undefined;
  workorderFileId.licenseFrontId = undefined;
  workorderFileId.licenseBackId = undefined;
  workorderFileId.certificateId = undefined;
  workorderFileId.invoiceId = undefined;

  showFlag.dialogVisible = false;
  // showFlag.insuranceVisible = false;
  showFlag.idCardRecognition = false;
  showFlag.licenseRecognition = false;
  showFlag.certificateRecognition = false;
  showFlag.invoiceRecognition = false;
  showFlag.businessLicenseRecognition = false;

  file.idCardBack = [];
  file.idCardFront = [];
  file.certificate = [];
  file.invoice = [];
  file.licenseFront = [];
  file.licenseBack = [];
}

const getUserOptionByMerchantId = async () => {
  loading.createBy = true;
  if (info.createMerchantId == null || info.createMerchantId == undefined){
    loading.createBy = false;
    createByOptions.value = [];
    return;
  }
  await selectUserOptionByMerchantId(info.createMerchantId).then(res=>{
    res = res.data;
    if (res.code == 200){
      createByOptions.value = res.data.map(item => {
        return {
          label: `${item.name} ${item.username}`,
          value: item.id
        }
      });
    }
  });
  loading.createBy = false;
}

// const getAreaOptionByMerchantId = async () => {
//   loading.area = true;
//   if (info.createMerchantId == null || info.createMerchantId == undefined){
//     loading.area = false;
//     areaOptions.value = [];
//     return;
//   }
//   await selectAreaByMerhcantId(info.createMerchantId).then(res=>{
//     res = res.data;
//     if (res.code == 200){
//       let areaCodeArray = res.data.map(item=>{
//         return getCascadeAreaCode(item.areaCode);
//       });
//       areaOptions.value = buildSelectorOptionByArray(areaCodeArray);
//     }
//   });
//   loading.area = false;
// }

// const getInsuranceCompanyOption = async (blurParam) => {
//   loading.insurance = true;
//   await selectInusranceCompanyOptions(blurParam).then(res=>{
//     res = res.data;
//     if (res.code == 200){
//       insuranceCompanyOptions.value = res.data.map(item => {
//         return {
//           label: `${item.code} ${item.name}`,
//           value: item.id
//         }
//       });
//     }
//   });
//   loading.insurance = false;
// }

const getInsuranceCompanyOption = async () => {
  let areaCode = info.areaCode[info.areaCode.length-1];
  if(areaCode == null || areaCode == undefined || areaCode == ''){
    insuranceCompanyOptions.value = [];
    return;
  }
  loading.insurance = true;
  await selectInsuranceCompanyByAreaCode(areaCode).then(res=>{
    res = res.data;
    if (res.code == 200){
      insuranceCompanyOptions.value = res.data.map(item => {
        return {
          label: `${item.code} ${item.name}`,
          value: item.id
        }
      });
    }
  });
  loading.insurance = false;
}

watch(
  () => info.createMerchantId, 
  async () => {
    await getUserOptionByMerchantId();
    // 当 newValue 变化时，主动调用获取提交人选项的方法
    if (pageStatus.value == 'ready'){
      if(Array.isArray(createByOptions.value) && createByOptions.value.length > 0){
        info.createBy = createByOptions.value[0].value;
      }
      else{
        info.createBy = undefined;
      }
    }
    else {
      pageStatus.value = 'ready';
    }
    
    // getAreaOptionByMerchantId();
  },
  {
    immediate: true, // 立即执行一次（页面挂载后，若有默认值则直接请求）
    deep: false // 基本类型，无需深度监听
  }
);

watch(
  () => info.areaCode,
  () => {
    getInsuranceCompanyOption();
  },
  {
    immediate: true,
    deep: false
  }
);

const handleExpandInsurance = () => {
  showFlag.insuranceVisible = !showFlag.insuranceVisible;
}

const getCreateMerchantOption = async (blurParam) => {
  loading.createMerchant = true;
  await selectDownstreamOption(blurParam).then(res=>{
    res = res.data;
    if(res.code == 200){
      createMerchantOptions.value = res.data.map(item => {
        return {
          label: `${item.code} ${item.name}`,
          value: item.id
        }
      });
    }
  });
  loading.createMerchant = false;
}



const handlePictureCardPreview = (file) => {
  dialogImageUrl.value = file.url
  showFlag.dialogVisible = true
}

const waitFileUpload = async () => {
  return new Promise((resolve, reject) => {
    const checkupInterval = setInterval(() => {
      if(!loadingFlag.idCardFront && !loadingFlag.idCardBack && !loadingFlag.licenseFront && !loadingFlag.licenseBack 
      && !loadingFlag.certificate && !loadingFlag.invoice && !loadingFlag.businessLicense){
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

const handleSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if(valid){
      Loading.open();
      try{
        await waitFileUpload();
      }catch(e){
        Message.error('文件上传超时，请稍后再试');
      }
      if (type == 'add'){
        addSubmit();
      }
      else {
        updateSubmit();
      }
    }
  });
};

const addSubmit = async () => {
  try{
    let data = buildInsertData();
    console.log(data);
    await insertWorkorder(data).then(res=>{
      res = res.data;
      if (res.code == 200){
        Message.success("添加成功");
        refresh.value++;
        routeToDetail(res.data.id);
      }
    });
  }
  finally{
    Loading.close();
  }
}

const getAllInsuranceOption = async () => {
  let res = await selectAllInsurance();
  res = res.data;
  if (res.code == 200){
    buildInsuranceOption(res.data);
  }
}

const buildInsuranceOption = (data) => {
  data.forEach(item => {
    item.options = jsonStrToObj(item.optionsJson);
    item.value = item.defaultOptionJson;
    item.deductibleOptions = jsonStrToObj(item.deductibleOptionsJson);
    item.deductibleValue = item.defaultDeductibleOptionJson;
  });
  let options = {};
  let type1 = data.filter(item => item.type==1);
  let type2 = data.filter(item => item.type==2);
  let type3 = data.filter(item => item.type==3);
  options.type1 = type1 != null ? type1 : [];
  options.type2 = type2 != null ? type2 : [];
  options.type3 = type3 != null ? type3 : [];
  insuranceOptions.value = options;
}

const buildInsertData = () => {
  const data = { ...info };
  data.vehicleLicense = undefined;
  data.vehicleInvoice = undefined;
  data.vehicleCertificate = undefined;
  if (data.ownerType == 0){
    data.organizationName = "";
    data.socialCreditCode = "";
  }
  else {
    data.ownerName = "";
    data.ownerIdNum = "";
  }
  if (info.type == 0){
    data.vehicleLicense = { ...info.vehicleLicense };
    data.vehicleLicense.registrationDate = convertDateToSecondTimestamp(info.vehicleLicense.registrationDate);
    data.vehicleLicense.issueDate = convertDateToSecondTimestamp(info.vehicleLicense.issueDate);
    data.vehicleLicense.seats = Math.trunc(transNumStrToNum(info.vehicleLicense.seats));  //  取整
    data.vehicleLicense.approvedLoadCapacity = Math.trunc(transNumStrToNum(info.vehicleLicense.approvedLoadCapacity));
    data.vehicleLicense.curbWeight = Math.trunc(transNumStrToNum(info.vehicleLicense.curbWeight));
    if (data.vehicleLicense.isTransfer == 1){
      // console.log(info.vehicleLicense.transferDate);
      data.vehicleLicense.transferDate = convertDateToSecondTimestamp(info.vehicleLicense.transferDate);
    }
    else {
      data.vehicleLicense.transferDate = null;
    }
  }
  else {
    data.vehicleCertificate = { ...info.vehicleCertificate };
    data.vehicleCertificate.curbWeight = Math.trunc(transNumStrToNum(info.vehicleCertificate.curbWeight));
    data.vehicleCertificate.seats = Math.trunc(transNumStrToNum(info.vehicleCertificate.seats));
    data.vehicleCertificate.displacement = Math.trunc(transNumStrToNum(info.vehicleCertificate.displacement));
    data.vehicleCertificate.approvedLoadCapacity = Math.trunc(transNumStrToNum(info.vehicleCertificate.approvedLoadCapacity));

    data.vehicleInvoice = { ...info.vehicleInvoice };
    data.vehicleInvoice.approvedLoadCapacity = transNumStrToNum(info.vehicleInvoice.invoiceAmount);
    data.vehicleInvoice.seats = Math.trunc(transNumStrToNum(info.vehicleInvoice.seats));
    data.vehicleInvoice.approvedLoadCapacity = Math.trunc(transNumStrToNum(info.vehicleInvoice.approvedLoadCapacity));
  }

  data.areaCode = info.areaCode[1];
  data.commercialInsuranceStartTime = convertDateToSecondTimestamp(info.commercialInsuranceStartTime);
  data.compulsoryInsuranceStartTime = convertDateToSecondTimestamp(info.compulsoryInsuranceStartTime);

  let workorderFileList = [];
  if(info.ownerType == 0){
    if (workorderFileId.idCardFrontId != null && workorderFileId.idCardFrontId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.idCardFrontId,
        type: 'idCardFront'
      });
    }
    if (workorderFileId.idCardBackId != null && workorderFileId.idCardBackId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.idCardBackId,
        type: 'idCardBack'
      });
    }
  }
  else {
    if (workorderFileId.businessLicenseId != null && workorderFileId.businessLicenseId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.businessLicenseId,
        type: 'businessLicense'
      });
    }
  }
  if (info.type == 0){
    if (workorderFileId.licenseFrontId != null && workorderFileId.licenseFrontId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.licenseFrontId,
        type: 'licenseFront'
      });
    }
    if (workorderFileId.licenseBackId != null && workorderFileId.licenseBackId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.licenseBackId,
        type: 'licenseBack'
      });
    }
  }
  else {
    if (workorderFileId.certificateId != null && workorderFileId.certificateId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.certificateId,
        type: 'certificate'
      });
    }
    if (workorderFileId.invoiceId != null && workorderFileId.invoiceId != undefined){
      workorderFileList.push({
        fileId: workorderFileId.invoiceId,
        type: 'invoice'
      });
    }
  }
  data.workorderFileList = workorderFileList;

  let workorderInsuranceList = [];

  let insurance = insuranceOptions.value.type1.concat(insuranceOptions.value.type2.concat(insuranceOptions.value.type3));
  insurance.forEach(item => {
    workorderInsuranceList.push({
      insuranceId: item.id,
      optionJson: item.value,
      deductibleOptionJson: item.deductibleValue,
    });
  });
  data.workorderInsuranceList = workorderInsuranceList;

  return data;
}

const buildInfo = () => {
  pageStatus.value = 'loading';
  info.ownerType = oriInfo.value.ownerType;
  info.type = oriInfo.value.type;
  info.ownerPhone = oriInfo.value.ownerPhone;
  info.ownerIdNum = oriInfo.value.ownerIdNum;
  info.ownerName = oriInfo.value.ownerName;
  info.organizationName = oriInfo.value.organizationName;
  info.socialCreditCode = oriInfo.value.socialCreditCode;
  info.commercialInsuranceStartTime = new Date(oriInfo.value.commercialInsuranceStartTime * 1000);
  info.compulsoryInsuranceStartTime = new Date(oriInfo.value.compulsoryInsuranceStartTime * 1000);
  info.remark = oriInfo.value.remark;
  
  info.createMerchantId = oriInfo.value.createMerchantId;
  createMerchantOptions.value = [{
    label: `${oriInfo.value.createMerchantCode} ${oriInfo.value.createMerchantName}`,
    value: oriInfo.value.createMerchantId
  }];

  info.insuranceMerchantId = oriInfo.value.insuranceMerchantId;
  insuranceCompanyOptions.value = [{
    label: `${oriInfo.value.insuranceMerchantCode} ${oriInfo.value.insuranceMerchantName}`,
    value: oriInfo.value.insuranceMerchantId
  }];

  info.createBy = oriInfo.value.createBy;
  info.areaCode = getCascadeAreaCode(oriInfo.value.areaCode);

  if (oriInfo.value.vehicleLicense != null && oriInfo.value.vehicleLicense != undefined){
    info.vehicleLicense = {...oriInfo.value.vehicleLicense};
    if (oriInfo.value.vehicleLicense.registrationDate != null && oriInfo.value.vehicleLicense.registrationDate != undefined){
      info.vehicleLicense.registrationDate = new Date(oriInfo.value.vehicleLicense.registrationDate * 1000);
    }
    if (oriInfo.value.vehicleLicense.issueDate != null && oriInfo.value.vehicleLicense.issueDate != undefined){
      info.vehicleLicense.issueDate = new Date(oriInfo.value.vehicleLicense.issueDate * 1000);
    }
    if (oriInfo.value.vehicleLicense.transferDate != null && oriInfo.value.vehicleLicense.transferDate != undefined){
      info.vehicleLicense.transferDate = new Date(oriInfo.value.vehicleLicense.transferDate * 1000);
    }
  } 
  if (oriInfo.value.vehicleInvoice != null && oriInfo.value.vehicleInvoice != undefined){
    info.vehicleInvoice = {...oriInfo.value.vehicleInvoice};
  }
  if (oriInfo.value.vehicleCertificate != null && oriInfo.value.vehicleCertificate != undefined){
    info.vehicleCertificate = {...oriInfo.value.vehicleCertificate};
  }

  if (oriInfo.value.ownerType == 0){
    showFlag.idCardRecognition = true;
  }
  else {
    showFlag.businessLicenseRecognition = true;
  }
  if (oriInfo.value.type == 0){
    showFlag.licenseRecognition = true;
  }
  else {
    showFlag.certificateRecognition = true;
    showFlag.invoiceRecognition = true;
  }
  if (oriInfo.value.workorderFileList != null && oriInfo.value.workorderFileList != undefined){
    oriInfo.value.workorderFileList.forEach(item => {
       if (item.type == 'idCardFront'){
        file.idCardFront = [{
          url: item.path
        }];
        workorderFileId.idCardFrontId = item.fileId;
       }
       if (item.type == 'idCardBack'){
        file.idCardBack = [{
          url: item.path
        }];
        workorderFileId.idCardBackId = item.fileId;
       }
       if (item.type == 'businessLicense'){
        file.businessLicense = [{
          url: item.path
        }];
        workorderFileId.businessLicenseId = item.fileId;
       }
       if (item.type == 'licenseFront'){
        file.licenseFront = [{
          url: item.path
        }];
        workorderFileId.licenseFrontId = item.fileId;
       }
       if (item.type == 'licenseBack'){
        file.licenseBack = [{
          url: item.path
        }];
        workorderFileId.licenseBackId = item.fileId;
       }
       if (item.type == 'certificate'){
        file.certificate = [{
          url: item.path
        }];
        workorderFileId.certificateId = item.fileId;
       }
       if (item.type == 'invoice'){
        file.invoice = [{
          url: item.path
        }];
        workorderFileId.invoiceId = item.fileId;
       }
    });
  }

  if (oriInfo.value.workorderInsuranceList != null && oriInfo.value.workorderInsuranceList != undefined){
    oriInfo.value.workorderInsuranceList.forEach(item => { 
      let found = false;

    const type1Option = insuranceOptions.value.type1.find(option => option.id == item.insuranceId);
    if (type1Option) {
      type1Option.value = item.optionJson;
      type1Option.deductibleValue = item.deductibleOptionJson;
      found = true;
    }
    
    if (!found) {
      const type2Option = insuranceOptions.value.type2.find(option => option.id == item.insuranceId);
      console.log(type2Option);
      if (type2Option) {
        type2Option.value = item.optionJson;
        type2Option.deductibleValue = item.deductibleOptionJson;
        found = true;
      }
    }
    
    if (!found) {
      const type3Option = insuranceOptions.value.type3.find(option => option.id == item.insuranceId);
      if (type3Option) {
        type3Option.value = item.optionJson;
        type3Option.deductibleValue = item.deductibleOptionJson;
      }
    }
    });
  }

}

const getDataById = async () => {
  let res = await selectWorkorderById(id);
  res = res.data;
  if(res.code == 200){
    oriInfo.value = res.data;
  }
}

const updateSubmit = async () => {
  try{
    let data = buildInsertData();
    data.id = id;
    data.code = oriInfo.value.code;
    await updateWorkorderBaseInfo(data).then(res => {
      res = res.data;
      if (res.code == 200){
        Message.success("更新成功");
        refresh.value++;
      }
    });
  }
  finally{
    Loading.close();
  }
}

const handleReset = () => {
  reset();
  buildInfo();
}

const handleIdCardFrontChange = async (uploadFile, uploadFiles) => {
  file.idCardFront = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.idCardFront = true;
    const rawFile = uploadFile.raw;
    await upload(rawFile).then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.idCardFrontId = res.data.id;
      }
    });
  }
  finally{
    loadingFlag.idCardFront = false;
  }
}

const handleIdCardBackChange = async (uploadFile, uploadFiles) => {
  file.idCardBack = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.idCardBack = true;
    const rawFile = uploadFile.raw;
    await imgRecognition(rawFile, "idCard").then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.idCardBackId = res.data.fileInfo.id;
        buildIdCardInfo(res.data.recognitionData);
      }
    });
  }
  finally{
    showFlag.idCardRecognition = true;
    loadingFlag.idCardBack = false;
  }
}

const buildIdCardInfo = (data) => {
  info.ownerName = data.name;
  info.ownerIdNum = data.idNum;
  showFlag.idCardRecognition = true;
}

const handleLicenseFrontChange = async (uploadFile, uploadFiles) => {
  file.licenseFront = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.licenseFront = true;
    const rawFile = uploadFile.raw;
    await imgRecognition(rawFile, "vehicleLicense").then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.licenseFrontId = res.data.fileInfo.id;
        buildLicenseInfo(res.data.recognitionData);
      }
    });
  }
  finally{
    showFlag.licenseRecognition = true;
    loadingFlag.licenseFront = false;
  }
}

const handleLicenseBackChange = async (uploadFile, uploadFiles) => {
  file.licenseBack = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.licenseBack = true;
    const rawFile = uploadFile.raw;
    await imgRecognition(rawFile, "vehicleLicense").then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.licenseBackId = res.data.fileInfo.id;
        buildLicenseInfo(res.data.recognitionData);
      }
    });
  }
  finally{
    showFlag.licenseRecognition = true;
    loadingFlag.licenseBack = false;
  }
}

const buildLicenseInfo = (data) => {
  if (data.registrationDate != null && data.registrationDate != undefined){
    data.registrationDate = new Date(data.registrationDate*1000);
  } 
  
  if (data.issueDate != null && data.issueDate != undefined){
    data.issueDate = new Date(data.issueDate*1000);
  }

  if (data.transferDate != null && data.transferDate != undefined){
    data.transferDate = new Date(data.transferDate*1000);
  }
  Object.assign(info.vehicleLicense, extractNonEmptyProps(data));
  showFlag.licenseRecognition = true;
}

const handleCertificateChange = async (uploadFile, uploadFiles) => {
  file.certificate = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.certificate = true;
    const rawFile = uploadFile.raw;
    await imgRecognition(rawFile, "vehicleCertificate").then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.certificateId = res.data.fileInfo.id;
        buildCertificateInfo(res.data.recognitionData);
      }
    });
  }
  finally{
    showFlag.certificateRecognition = true;
    loadingFlag.certificate = false;
  }
}

const buildCertificateInfo = (data) => {
  info.vehicleCertificate = data;
  showFlag.certificateRecognition = true;
}

const handleInvoiceChange = async (uploadFile, uploadFiles) => {
  file.invoice = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.invoice = true;
    const rawFile = uploadFile.raw;
    await imgRecognition(rawFile, "vehicleInvoice").then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.invoiceId = res.data.fileInfo.id;
        buildInvoiceInfo(res.data.recognitionData);
      }
    });
  }
  finally{
    showFlag.invoiceRecognition = true;
    loadingFlag.invoice = false;
  }
}

const buildInvoiceInfo = (data) => {
  info.vehicleInvoice = data;
  showFlag.invoiceRecognition = true;
}

const handleBusinessLicenseChange = async (uploadFile, uploadFiles) => {
  file.businessLicense = validFileSize(uploadFiles);
  if (uploadFiles.length == 0){
    return;
  }
  try{
    loadingFlag.businessLicense = true;
    const rawFile = uploadFile.raw;
    await imgRecognition(rawFile, "businessLicense").then(res=>{
      res = res.data;
      if (res.code == 200){
        workorderFileId.businessLicenseId = res.data.fileInfo.id;
        buildBusinessLicenseInfo(res.data.recognitionData);
      }
    });
  }
  finally{
    showFlag.businessLicenseRecognition = true;
    loadingFlag.businessLicense = false;
  }
}

const buildBusinessLicenseInfo = (data) => {
  info.organizationName = data.organizationName;
  info.socialCreditCode = data.socialCreditCode;
  showFlag.businessLicenseRecognition = true;
}

const handleDownload = (ElUploadFile, type) => {
  let url;
  let name = Date.now().toString();
  if (type == 'idCardFront' && file.idCardFront.length > 0){
    name = '身份证国徽面';
    url = file.idCardFront[0].url;
  }
  if (type == 'idCardBack' && file.idCardBack.length > 0){
    name = '身份证头像面';
    url = file.idCardBack[0].url;
  }
  if (type == 'licenseFront' && file.licenseFront.length > 0){
    name = '行驶证正页';
    url = file.licenseFront[0].url;
  }
  if (type == 'licenseBack' && file.licenseBack.length > 0){
    name = '行驶证副页';
    url = file.licenseBack[0].url;
  }
  if (type == 'certificate' && file.certificate.length > 0){
    name = '合格证';
    url = file.certificate[0].url;
  }
  if (type == 'invoice' && file.invoice.length > 0){
    name = '购车发票';
    url = file.invoice[0].url;
  }
  if (type == 'businessLicense' && file.businessLicense.length > 0){
    name = '营业执照';
    url = file.businessLicense[0].url;
  }
  if (url == null || url == undefined || url == ''){
    return;
  }
  downloadByUrl(url, name);
}

const handleRemove = (ElUploadFile, type) => {
  if (type == 'idCardFront'){
    file.idCardFront = [];
  }
  if (type == 'idCardBack'){
    file.idCardBack = [];
  }
  if (type == 'licenseFront'){
    file.licenseFront = [];
  }
  if (type == 'licenseBack'){
    file.licenseBack = [];
  }
  if (type == 'certificate'){
    file.certificate = [];
  }
  if (type == 'invoice'){
    file.invoice = [];
  }
  if (type == 'businessLicense'){
    file.businessLicense = [];
  }
}

const handleNumberInputChange = (event, type) => {
  let newValue = event.target.value;
  if (newValue == '' || isNumber(newValue)){
    return;
  }
  if (type == "licenseSeats"){
    info.vehicleLicense.seats = "";
  }
  if (type == "licenseApprovedLoadCapacity"){
    info.vehicleLicense.approvedLoadCapacity = "";
  }
  if (type == "licenseCurbWeight"){
    info.vehicleLicense.curbWeight = "";
  }
  if (type == "certificateSeats"){
    info.vehicleCertificate.seats = "";
  }
  if (type == "certificateCurbWeight"){
    info.vehicleCertificate.curbWeight = "";
  }
  if (type == "certificateDisplacement"){
    info.vehicleCertificate.displacement = "";
  }
  if (type == "certificateApprovedLoadCapacity"){
    info.vehicleCertificate.approvedLoadCapacity = "";
  }
  if (type == 'invoiceAmount'){
    info.vehicleInvoice.invoiceAmount = "";
  }
  if (type == "invoiceSeats"){
    info.vehicleInvoice.seats = "";
  }
  if (type == "invoiceApprovedLoadCapacity"){
    info.vehicleInvoice.approvedLoadCapacity = "";
  }
}

const routeToDetail = (id) => {
  sessionStorage.setItem('workorderDetailType', 'handle');
  sessionStorage.setItem('workorderId', id);
  router.push({
    path: '/home/detailWorkorder',
  });
};

</script>


<style scoped>

</style>
