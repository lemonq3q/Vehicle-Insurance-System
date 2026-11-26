<template>
  <div class="container_body card">
    <div class="title_container">
      <div class="search_title" style="margin: 0;">基础信息</div>
      <OcrApplyButton v-if="hasOcrCandidates" class="ocr-global-apply" size="large" @click="applyAllOcrCandidates()">全部应用识别结果</OcrApplyButton>
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
        <el-form-item prop="submitterName" label="提交人">
          <el-input v-model="info.submitterName" maxlength="100" placeholder="请输入提交人" />
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('身份证头像面', 1)"
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('身份证国徽面', 1)"
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
        <el-form-item v-if="info.ownerType == 0" label-position="top" class="cascade_form_container">
          <template #label>
            <div class="ocr-section-heading">
              <span>身份证识别信息</span>
              <OcrApplyButton v-if="hasOcrCandidatesFor(['idCardBack'])" size="large" @click="applyAllOcrCandidates(['idCardBack'])">全部应用</OcrApplyButton>
            </div>
          </template>
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
              <OcrCandidate :candidates="getOcrCandidates('ownerName')" @apply="applyOcrCandidate('ownerName', $event)" />
            </el-form-item>
            <el-form-item label="证件号码">
              <el-input
              v-model="info.ownerIdNum"
              class="responsive-input"
              placeholder="请补充"
              maxlength="50"
              />
              <OcrCandidate :candidates="getOcrCandidates('ownerIdNum')" @apply="applyOcrCandidate('ownerIdNum', $event)" />
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('营业执照', 1)"
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
        <el-form-item v-if="info.ownerType == 1" label-position="top" class="cascade_form_container">
          <template #label>
            <div class="ocr-section-heading">
              <span>营业执照识别信息</span>
              <OcrApplyButton v-if="hasOcrCandidatesFor(['businessLicense'])" size="large" @click="applyAllOcrCandidates(['businessLicense'])">全部应用</OcrApplyButton>
            </div>
          </template>
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
              <OcrCandidate :candidates="getOcrCandidates('organizationName')" @apply="applyOcrCandidate('organizationName', $event)" />
            </el-form-item>
            <el-form-item label="社会信用代码">
              <el-input
              v-model="info.socialCreditCode"
              class="responsive-input"
              placeholder="请补充"
              maxlength="95"
              />
              <OcrCandidate :candidates="getOcrCandidates('socialCreditCode')" @apply="applyOcrCandidate('socialCreditCode', $event)" />
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('行驶证副页', 1)"
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('行驶证主页', 1)"
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
        <el-form-item v-if="info.type==0" label-position="top" class="cascade_form_container">
          <template #label>
            <div class="ocr-section-heading">
              <span>行驶证识别信息</span>
              <OcrApplyButton v-if="hasOcrCandidatesFor(['licenseFront', 'licenseBack'])" size="large" @click="applyAllOcrCandidates(['licenseFront', 'licenseBack'])">全部应用</OcrApplyButton>
            </div>
          </template>
          <div class="params_container cascade_params_container cascade_form">
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车牌号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.licensePlate" 
                placeholder="请补充"
                maxlength="45"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.licensePlate')" @apply="applyOcrCandidate('vehicleLicense.licensePlate', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车辆类型</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.vehicleType" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.vehicleType')" @apply="applyOcrCandidate('vehicleLicense.vehicleType', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">所有人</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.ownerName" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.ownerName')" @apply="applyOcrCandidate('vehicleLicense.ownerName', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">使用性质</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.usageNature" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.usageNature')" @apply="applyOcrCandidate('vehicleLicense.usageNature', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">厂牌型号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.brandModel" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.brandModel')" @apply="applyOcrCandidate('vehicleLicense.brandModel', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车架号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.vehicleCode" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.vehicleCode')" @apply="applyOcrCandidate('vehicleLicense.vehicleCode', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发动机号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleLicense.engineCode" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.engineCode')" @apply="applyOcrCandidate('vehicleLicense.engineCode', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.registrationDate')" @apply="applyOcrCandidate('vehicleLicense.registrationDate', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.issueDate')" @apply="applyOcrCandidate('vehicleLicense.issueDate', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.seats')" @apply="applyOcrCandidate('vehicleLicense.seats', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.approvedLoadCapacity')" @apply="applyOcrCandidate('vehicleLicense.approvedLoadCapacity', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.curbWeight')" @apply="applyOcrCandidate('vehicleLicense.curbWeight', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">是否过户</span>
              <div class="form_content cascade_form_content">
                <el-radio-group v-model="info.vehicleLicense.isTransfer">
                  <el-radio :value="1" style="margin-right: 10px;">过户</el-radio>
                  <el-radio :value="0">非过户</el-radio>
                </el-radio-group>
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.isTransfer')" @apply="applyOcrCandidate('vehicleLicense.isTransfer', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleLicense.transferDate')" @apply="applyOcrCandidate('vehicleLicense.transferDate', $event)" />
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('合格证', 1)"
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
        <el-form-item v-if="info.type==1" label-position="top" class="cascade_form_container">
          <template #label>
            <div class="ocr-section-heading">
              <span>合格证识别信息</span>
              <OcrApplyButton v-if="hasOcrCandidatesFor(['certificate'])" size="large" @click="applyAllOcrCandidates(['certificate'])">全部应用</OcrApplyButton>
            </div>
          </template>
          <div class="params_container cascade_params_container cascade_form">
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">厂牌型号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.brandModel" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.brandModel')" @apply="applyOcrCandidate('vehicleCertificate.brandModel', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车辆类型</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.vehicleType" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.vehicleType')" @apply="applyOcrCandidate('vehicleCertificate.vehicleType', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车架号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.vehicleCode" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.vehicleCode')" @apply="applyOcrCandidate('vehicleCertificate.vehicleCode', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发动机号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleCertificate.engineCode" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.engineCode')" @apply="applyOcrCandidate('vehicleCertificate.engineCode', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.curbWeight')" @apply="applyOcrCandidate('vehicleCertificate.curbWeight', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.seats')" @apply="applyOcrCandidate('vehicleCertificate.seats', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.displacement')" @apply="applyOcrCandidate('vehicleCertificate.displacement', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleCertificate.approvedLoadCapacity')" @apply="applyOcrCandidate('vehicleCertificate.approvedLoadCapacity', $event)" />
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
          :limit="1"
          :on-exceed="() => warnUploadLimit('购车发票', 1)"
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
        <el-form-item v-if="info.type==1" label-position="top" class="cascade_form_container">
          <template #label>
            <div class="ocr-section-heading">
              <span>购车发票识别信息</span>
              <OcrApplyButton v-if="hasOcrCandidatesFor(['invoice'])" size="large" @click="applyAllOcrCandidates(['invoice'])">全部应用</OcrApplyButton>
            </div>
          </template>
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.invoiceAmount')" @apply="applyOcrCandidate('vehicleInvoice.invoiceAmount', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">购方名称</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.buyerName" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.buyerName')" @apply="applyOcrCandidate('vehicleInvoice.buyerName', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">购方身份证</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.buyerIdNum" 
                placeholder="请补充"
                maxlength="45"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.buyerIdNum')" @apply="applyOcrCandidate('vehicleInvoice.buyerIdNum', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车辆类型</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.vehicleType" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.vehicleType')" @apply="applyOcrCandidate('vehicleInvoice.vehicleType', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">厂牌型号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.brandModel" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.brandModel')" @apply="applyOcrCandidate('vehicleInvoice.brandModel', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">车架号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.vehicleCode" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.vehicleCode')" @apply="applyOcrCandidate('vehicleInvoice.vehicleCode', $event)" />
              </div>
            </div>
            <div class="param_item cascade_param_item">
              <span class="form_title cascade_form_title">发动机号</span>
              <div class="form_content cascade_form_content">
                <el-input 
                v-model="info.vehicleInvoice.engineCode" 
                placeholder="请补充"
                maxlength="95"/>
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.engineCode')" @apply="applyOcrCandidate('vehicleInvoice.engineCode', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.seats')" @apply="applyOcrCandidate('vehicleInvoice.seats', $event)" />
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
              <OcrCandidate :candidates="getOcrCandidates('vehicleInvoice.approvedLoadCapacity')" @apply="applyOcrCandidate('vehicleInvoice.approvedLoadCapacity', $event)" />
              </div>
            </div>
          </div>
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
import { computed, nextTick, reactive, ref, onMounted, watch, defineProps, defineEmits } from 'vue';
import { validatePhoneNumber, validFileSize } from '@/utils/validate';
import { insertWorkorder, selectWorkorderById, updateWorkorderBaseInfo } from '@/api/workorder';
import Message from '@/utils/message';
import { warnUploadLimit } from '@/utils/uploadFeedback';
import { imgRecognition } from '@/api/ocr';
import { selectAllInsurance } from '@/api/insurance';
import { jsonStrToObj, transNumStrToNum } from '@/utils/convert';
import { downloadByUrl, uploadToOss } from '@/api/file';
import { isNumber } from '@/utils/validate';
import { selectDownstreamOption } from '@/api/downstream';
import { selectDownstreamById } from '@/api/downstream';
// import { selectInusranceCompanyOptions } from '@/api/upstream';
// import { selectAreaByMerhcantId } from '@/api/merchant';
import { getCascadeAreaCode } from '@/utils/ChinaCitys';
import { convertDateToSecondTimestamp } from '@/utils/time';
import { normalizeOcrCandidates, consumeOcrCandidates, fillEmptyOcrFields } from '@/utils/ocrCandidates';
import OcrCandidate from './OcrCandidate.vue';
import OcrApplyButton from './OcrApplyButton.vue';
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
/**
 * * 将组件内部新增或更新成功事件转换为父组件 refresh 事件，使外层列表无需读取编辑器内部状态即可刷新。
 */
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
  submitterName: [{ max: 100, message: '提交人最多100字', trigger: 'blur' }],
  areaCode: [
    { required: true, message: '请选择业务区域', trigger: 'blur' }
  ],
  ownerPhone: [
    { required: true, message: '请输入车主电话', trigger: 'blur' },
    { min: 1, max: 100, message: '输入内容过长', trigger: 'blur' },
    { validator: validatePhoneNumber, trigger: 'blur' }
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
  submitterName: '',
  areaCode: [],
  ownerPhone: "",
  ownerIdNum: "",
  ownerName: "",
  commercialInsuranceStartTime: null,
  compulsoryInsuranceStartTime: null,
  nonMotorInsuranceStartTime: null,
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

/**
 * 将各张证件的候选与人工表单分开保存；正副页保留独立来源。
 * 上传令牌阻止重传、移除或撤销后的迟到响应污染当前编辑，候选不随工单提交。
 */
const ocrResults = reactive({});
const ocrUploadVersions = {};
const ocrSources = {
  idCardBack: '身份证', businessLicense: '营业执照', licenseFront: '行驶证正页',
  licenseBack: '行驶证副页', certificate: '合格证', invoice: '发票'
};

/**
 * 按车主和车辆类型过滤来源，避免切换场景后应用隐藏证件的信息。
 * @param {string} source 上传证件标识。
 * @returns {boolean} 是否属于当前录入场景，不改变表单。
 */
const isActiveOcrSource = (source) => {
  if (source === 'idCardBack') return info.ownerType === 0;
  if (source === 'businessLicense') return info.ownerType === 1;
  if (source === 'licenseFront' || source === 'licenseBack') return info.type === 0;
  return info.type === 1;
};

/**
 * 接收当前证件识别结果，空字段自动补齐，已填写字段保留候选供人工复核。
 * @param {string} source 通过上传版本检查后的证件来源。
 * @param {object} values 已完成字段白名单和日期转换的候选映射。
 * @returns {void} 更新表单和待确认候选；隐藏场景只保留候选，避免迟到响应写入其他场景。
 */
const receiveOcrCandidates = (source, values) => {
  ocrResults[source] = isActiveOcrSource(source) ? fillEmptyOcrFields(info, values) : values;
};

/**
 * 读取原字段的全部识别来源，仅生成候选展示，不覆盖已有信息。
 * @param {string} path 工单表单字段路径。
 * @returns {Array} 含来源名称及原类型值的候选集合。
 */
const getOcrCandidates = (path) => Object.keys(ocrSources)
  .filter(source => isActiveOcrSource(source) && ocrResults[source]?.[path] !== undefined)
  .map(source => ({ source, label: ocrSources[source], value: ocrResults[source][path] }));

const hasOcrCandidates = computed(() => Object.keys(ocrResults)
  .some(source => isActiveOcrSource(source) && Object.keys(ocrResults[source]).length > 0));

/**
 * 用户点击应用后才更新对应字段；复制日期避免候选和表单共享可变对象。
 * @param {string} path 字段白名单生成的工单路径。
 * @param {*} value 用户选定的非空候选，保留表单所需类型。
 * @returns {void} 修改当前编辑并消费该字段的所有来源候选，用户仍需保存工单。
 */
const applyOcrCandidate = (path, value) => {
  const parts = path.split('.');
  const target = parts.length === 2 ? info[parts[0]] : info;
  target[parts[parts.length - 1]] = value instanceof Date ? new Date(value.getTime()) : value;
  consumeOcrCandidates(ocrResults, path);
};

/**
 * 控制各识别信息区的全部应用按钮，仅在该区仍有未确认候选时显示。
 * @param {string[]} sources 区块对应的证件来源，行驶证包含正副两页。
 * @returns {boolean} 是否存在当前车辆/车主场景可应用的候选。
 */
const hasOcrCandidatesFor = (sources) => sources.some(source => isActiveOcrSource(source)
  && Object.keys(ocrResults[source] || {}).length > 0);

/**
 * 应用当前场景全部有效候选；正副页冲突时优先正页，与候选显示顺序一致。
 * 缺失字段不清空，应用后的候选立即移除；字段仍可人工编辑，不触发提交。
 * @param {string[]} sources 可选的证件来源范围，未提供时应用当前场景全部证件。
 */
const applyAllOcrCandidates = (sources) => {
  const paths = new Set(Object.values(ocrResults).flatMap(values => Object.keys(values)));
  paths.forEach(path => {
    const candidate = getOcrCandidates(path).find(item => !sources || sources.includes(item.source));
    if (candidate) applyOcrCandidate(path, candidate.value);
  });
};

// const areaOptions = ref([]);



/**
 * 初始化工单编辑器：始终加载险种定义，更新模式额外并行查询工单详情，随后把持久化数据还原为表单状态。
 * 全局遮罩覆盖整个初始化过程，并在接口异常时通过 finally 关闭。
 */
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

/**
 * 清除证件文件关联、预览列表和所有 OCR 候选，供编辑撤销或重新回填前恢复干净状态。
 * 这里只重置上传相关状态，不直接清空工单主体表单。
 */
const reset = () => {
  Object.keys(file).forEach(source => clearUploadState(source));
  showFlag.dialogVisible = false;
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

/**

 * * 使用地区级联选择的最后一级编码查询当地可承保公司；地区为空时同步清空保险公司选项。

 */
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

/**
 * 切换提交店铺后从下游主体带入联系人字符串；编辑初始化保留历史提交人快照。
 * 异步返回须与当前选店一致，防止快速切换店铺后错误覆盖，用户可在带入后自由编辑。
 */
watch(() => info.createMerchantId, async (merchantId) => {
  if (pageStatus.value !== 'ready') {
    pageStatus.value = 'ready';
    return;
  }
  if (!merchantId) { info.submitterName = ''; return; }
  const originalName = info.submitterName;
  const res = await selectDownstreamById(merchantId);
  if (info.createMerchantId === merchantId && info.submitterName === originalName && res.data.code === 200) {
    info.submitterName = res.data.data.contact || '';
  }
});

/**

 * * 监听业务地区变化，联动刷新该地区可选择的保险公司。

 */
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

/**

 * * 切换险种明细区域的展开状态，不修改已经选择的险种和保额数据。

 */
const handleExpandInsurance = () => {
  showFlag.insuranceVisible = !showFlag.insuranceVisible;
}

/**

 * * 根据输入关键字远程查询工单创建机构，并组合机构编码和名称作为下拉标签。

 */
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



/**



 * * 将当前上传文件地址写入预览弹窗，使用户在提交前核对证件影像。



 */
const handlePictureCardPreview = (file) => {
  dialogImageUrl.value = file.url
  showFlag.dialogVisible = true
}

/**
 * 在提交工单前轮询七类证件的上传状态，确保请求体中的文件 ID 均已生成。
 * 每 200ms 检查一次，30 秒仍未完成则拒绝提交，避免保存只有本地预览而没有 OSS 文件的工单。
 */
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

/**
 * 先执行表单规则校验，再等待全部异步文件上传结束，最后依据 add/update 模式进入对应保存流程。
 * 上传超时会提示用户并主动关闭遮罩，不会发送不完整工单数据。
 */
const handleSubmit = (formEl) => {
  if (!formEl) return;
  formEl.validate(async (valid) => {
    if(valid){
      /**
       * 工单必须携带当前企业的险种快照。若企业尚未配置险种，继续提交只会形成没有业务意义的
       * 空险种工单，因此在进入上传和保存流程前直接提示，避免产生无效请求。
       */
      const insuranceCount = insuranceOptions.value.type1.length
        + insuranceOptions.value.type2.length
        + insuranceOptions.value.type3.length;
      if (insuranceCount === 0) {
        Message.warning('当前企业尚未配置险种，无法创建工单，请先联系管理员完成险种配置');
        return;
      }
      Loading.open();
      try{
        await waitFileUpload();
      }catch(e){
        Message.error('文件上传超时，请稍后再试');
        Loading.close();
        return;
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

/**

 * * 构建新增工单请求并持久化；成功后通知父列表刷新，并跳转到新工单详情继续处理后续流程。

 */
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

/**

 * * 查询系统险种字典，并交给选项构建逻辑解析保额、免赔额默认值及险种分组。

 */
const getAllInsuranceOption = async () => {
  let res = await selectAllInsurance();
  res = res.data;
  if (res.code == 200){
    buildInsuranceOption(res.data);
  }
}

/**

 * * 解析险种记录中的 JSON 选项和默认值，再按商业险、交强险及附加险类型分组供表单渲染。

 */
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

/**
 * 将响应式表单转换为新增或更新接口的工单聚合数据。
 * 根据个人/企业车主和行驶证/发票/合格证录入类型清除互斥字段，日期转换为秒级时间戳，数值字段
 * 规范为整数或金额，并把所选险种、证件文件 ID 组装成后端可一次持久化的关联列表。
 */
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
    data.vehicleInvoice.invoiceAmount = transNumStrToNum(info.vehicleInvoice.invoiceAmount);
    data.vehicleInvoice.seats = Math.trunc(transNumStrToNum(info.vehicleInvoice.seats));
    data.vehicleInvoice.approvedLoadCapacity = Math.trunc(transNumStrToNum(info.vehicleInvoice.approvedLoadCapacity));
  }

  data.areaCode = info.areaCode[1];
  /*
   * 创建阶段尚未取得保单起期，不预填当天日期；编辑历史工单时保留已有日期。
   * 三类保单的实际起期在承保资料中录入，空值交由接口按未填写处理。
   */
  data.commercialInsuranceStartTime = info.commercialInsuranceStartTime == null ? null : convertDateToSecondTimestamp(info.commercialInsuranceStartTime);
  data.compulsoryInsuranceStartTime = info.compulsoryInsuranceStartTime == null ? null : convertDateToSecondTimestamp(info.compulsoryInsuranceStartTime);
  data.nonMotorInsuranceStartTime = info.nonMotorInsuranceStartTime == null ? null : convertDateToSecondTimestamp(info.nonMotorInsuranceStartTime);

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

/**
 * 将详情接口返回的工单聚合数据回填到编辑表单。
 * 该过程恢复机构与保险公司下拉选项、日期对象、车辆证件、文件预览和历史险种选择；接口中不存在于
 * 当前险种字典的旧选项会按类型回退匹配，保证历史工单仍可查看和再次保存。
 */
const buildInfo = () => {
  pageStatus.value = 'loading';
  info.ownerType = oriInfo.value.ownerType;
  info.type = oriInfo.value.type;
  info.ownerPhone = oriInfo.value.ownerPhone;
  info.ownerIdNum = oriInfo.value.ownerIdNum;
  info.ownerName = oriInfo.value.ownerName;
  info.organizationName = oriInfo.value.organizationName;
  info.socialCreditCode = oriInfo.value.socialCreditCode;
  info.commercialInsuranceStartTime = oriInfo.value.commercialInsuranceStartTime == null ? null : new Date(oriInfo.value.commercialInsuranceStartTime * 1000);
  info.compulsoryInsuranceStartTime = oriInfo.value.compulsoryInsuranceStartTime == null ? null : new Date(oriInfo.value.compulsoryInsuranceStartTime * 1000);
  info.nonMotorInsuranceStartTime = oriInfo.value.nonMotorInsuranceStartTime == null ? null : new Date(oriInfo.value.nonMotorInsuranceStartTime * 1000);
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

  info.submitterName = oriInfo.value.submitterName || '';
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

  nextTick(() => { pageStatus.value = 'ready'; });

}

/**

 * * 按组件传入的工单 ID 查询完整详情，并暂存原始数据供 buildInfo 回填和更新时保留工单编码。

 */
const getDataById = async () => {
  let res = await selectWorkorderById(id);
  res = res.data;
  if(res.code == 200){
    oriInfo.value = res.data;
  }
}

/**

 * * 在统一请求体基础上补充原工单 ID 和业务编码，保存基础资料修改；成功后通知父列表刷新。

 */
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

/**

 * * 丢弃当前未保存的文件和表单编辑，再从 oriInfo 重新构建原始工单状态。

 */
const handleReset = () => {
  reset();
  buildInfo();
}

/**

 * * 为上传项提供可预览地址：优先复用已有远程或 Blob URL，本地 File 则临时创建 Object URL。

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

 * * 将 Element Plus 本地上传项与后端系统文件记录合并，统一补齐文件 ID、名称和预览地址。

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
 * 校验上传项包含原始 File 后执行 OSS 直传，并要求后端返回可关联的文件记录。
 * 返回值同时包含业务 fileInfo 和组件预览项，供普通证件上传复用。
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
 * 先把证件原图写入 OSS，再以系统文件信息调用指定 OCR 类型。
 * OCR 失败会整体按上传失败处理；成功时返回最终文件记录、识别字段和本地预览项。
 */
const handleOcrFileUpload = async (uploadFile, ocrType) => {
  const { fileInfo, uploadFileItem } = await handleSingleFileUpload(uploadFile);
  const response = await imgRecognition(fileInfo, ocrType);
  const res = response.data;
  if (res.code != 200){
    throw new Error(res.msg || 'OCR识别失败，请重新上传');
  }
  return {
    fileInfo: res.data?.fileInfo || fileInfo,
    recognitionData: res.data?.recognitionData,
    uploadFileItem
  };
}

/**

 * * 按证件类型同时清空上传列表和对应工单文件 ID，保证移除或失败后请求体不引用旧文件。

 */
const clearUploadState = (type) => {
  ocrUploadVersions[type] = (ocrUploadVersions[type] || 0) + 1;
  delete ocrResults[type];
  loadingFlag[type] = false;
  const fileIdMap = {
    idCardFront: 'idCardFrontId',
    idCardBack: 'idCardBackId',
    licenseFront: 'licenseFrontId',
    licenseBack: 'licenseBackId',
    certificate: 'certificateId',
    invoice: 'invoiceId',
    businessLicense: 'businessLicenseId'
  };
  file[type] = [];
  const fileIdKey = fileIdMap[type];
  if (fileIdKey) {
    workorderFileId[fileIdKey] = undefined;
  }
}

/**

 * * 统一回滚失败证件的文件状态和候选，保留人工已录入字段，并向用户展示具体错误原因。

 */
const handleUploadFail = (type, message = '图片上传失败，请重新上传') => {
  clearUploadState(type);
  Message.error(message);
}

/**

 * * 校验并上传身份证国徽面，仅保存文件关联，不使用该面覆盖车主姓名和证件号码。

 */
const handleIdCardFrontChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.idCardFront = (ocrUploadVersions.idCardFront || 0) + 1;
  delete ocrResults.idCardFront;
  const validFiles = validFileSize(uploadFiles);
  file.idCardFront = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.idCardFront = true;
    const { fileInfo, uploadFileItem } = await handleSingleFileUpload(uploadFile);
    if (ocrUploadVersions.idCardFront !== version) return;
    workorderFileId.idCardFrontId = fileInfo.id;
    file.idCardFront = [uploadFileItem];
  }
  catch(error){
    if (ocrUploadVersions.idCardFront !== version) return;
    handleUploadFail('idCardFront', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.idCardFront === version) loadingFlag.idCardFront = false;
  }
}

/**

 * * 上传身份证人像面并执行身份证 OCR，成功后保存文件 ID 并生成姓名与证件号候选。

 */
const handleIdCardBackChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.idCardBack = (ocrUploadVersions.idCardBack || 0) + 1;
  delete ocrResults.idCardBack;
  const validFiles = validFileSize(uploadFiles);
  file.idCardBack = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.idCardBack = true;
    const { fileInfo, recognitionData, uploadFileItem } = await handleOcrFileUpload(uploadFile, "idCard");
    if (ocrUploadVersions.idCardBack !== version) return;
    workorderFileId.idCardBackId = fileInfo.id;
    file.idCardBack = [uploadFileItem];
    buildIdCardInfo(recognitionData);
  }
  catch(error){
    if (ocrUploadVersions.idCardBack !== version) return;
    handleUploadFail('idCardBack', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.idCardBack === version) loadingFlag.idCardBack = false;
  }
}

/**
 * 接收本次证件的非空 OCR 结果，空字段自动补齐，已有内容保留候选供人工应用。
 * 日期由统一转换处理，行驶证正副页分别保留来源，不覆盖识别期间新增的手工内容。
 */
const buildIdCardInfo = (data) => {
  receiveOcrCandidates('idCardBack', normalizeOcrCandidates(data, { name: 'ownerName', idNum: 'ownerIdNum' }));

}

/**

 * * 上传行驶证正页并执行车辆行驶证 OCR，成功后展示车辆登记候选。

 */
const handleLicenseFrontChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.licenseFront = (ocrUploadVersions.licenseFront || 0) + 1;
  delete ocrResults.licenseFront;
  const validFiles = validFileSize(uploadFiles);
  file.licenseFront = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.licenseFront = true;
    const { fileInfo, recognitionData, uploadFileItem } = await handleOcrFileUpload(uploadFile, "vehicleLicense");
    if (ocrUploadVersions.licenseFront !== version) return;
    workorderFileId.licenseFrontId = fileInfo.id;
    file.licenseFront = [uploadFileItem];
    buildLicenseInfo(recognitionData, 'licenseFront');
  }
  catch(error){
    if (ocrUploadVersions.licenseFront !== version) return;
    handleUploadFail('licenseFront', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.licenseFront === version) loadingFlag.licenseFront = false;
  }
}

/**

 * * 上传行驶证副页并使用同一行驶证 OCR 规则保留车辆核定载重、尺寸等候选。

 */
const handleLicenseBackChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.licenseBack = (ocrUploadVersions.licenseBack || 0) + 1;
  delete ocrResults.licenseBack;
  const validFiles = validFileSize(uploadFiles);
  file.licenseBack = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.licenseBack = true;
    const { fileInfo, recognitionData, uploadFileItem } = await handleOcrFileUpload(uploadFile, "vehicleLicense");
    if (ocrUploadVersions.licenseBack !== version) return;
    workorderFileId.licenseBackId = fileInfo.id;
    file.licenseBack = [uploadFileItem];
    buildLicenseInfo(recognitionData, 'licenseBack');
  }
  catch(error){
    if (ocrUploadVersions.licenseBack !== version) return;
    handleUploadFail('licenseBack', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.licenseBack === version) loadingFlag.licenseBack = false;
  }
}

/**
 * 接收本次证件的非空 OCR 结果，空字段自动补齐，已有内容保留候选供人工应用。
 * 日期由统一转换处理，行驶证正副页分别保留来源，不覆盖识别期间新增的手工内容。
 */
const buildLicenseInfo = (data, source) => {
  receiveOcrCandidates(source, normalizeOcrCandidates(data, info.vehicleLicense, 'vehicleLicense'));

}

/**

 * * 上传车辆合格证并执行合格证 OCR，保存文件关联后保留车型、车架号及车辆参数候选。

 */
const handleCertificateChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.certificate = (ocrUploadVersions.certificate || 0) + 1;
  delete ocrResults.certificate;
  const validFiles = validFileSize(uploadFiles);
  file.certificate = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.certificate = true;
    const { fileInfo, recognitionData, uploadFileItem } = await handleOcrFileUpload(uploadFile, "vehicleCertificate");
    if (ocrUploadVersions.certificate !== version) return;
    workorderFileId.certificateId = fileInfo.id;
    file.certificate = [uploadFileItem];
    buildCertificateInfo(recognitionData);
  }
  catch(error){
    if (ocrUploadVersions.certificate !== version) return;
    handleUploadFail('certificate', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.certificate === version) loadingFlag.certificate = false;
  }
}

/**
 * 接收本次证件的非空 OCR 结果，空字段自动补齐，已有内容保留候选供人工应用。
 * 日期由统一转换处理，行驶证正副页分别保留来源，不覆盖识别期间新增的手工内容。
 */
const buildCertificateInfo = (data) => {
  receiveOcrCandidates('certificate', normalizeOcrCandidates(data, info.vehicleCertificate, 'vehicleCertificate'));

}

/**

 * * 上传机动车发票并执行发票 OCR，成功后保留购车金额、买方和车辆资料候选。

 */
const handleInvoiceChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.invoice = (ocrUploadVersions.invoice || 0) + 1;
  delete ocrResults.invoice;
  const validFiles = validFileSize(uploadFiles);
  file.invoice = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.invoice = true;
    const { fileInfo, recognitionData, uploadFileItem } = await handleOcrFileUpload(uploadFile, "vehicleInvoice");
    if (ocrUploadVersions.invoice !== version) return;
    workorderFileId.invoiceId = fileInfo.id;
    file.invoice = [uploadFileItem];
    buildInvoiceInfo(recognitionData);
  }
  catch(error){
    if (ocrUploadVersions.invoice !== version) return;
    handleUploadFail('invoice', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.invoice === version) loadingFlag.invoice = false;
  }
}

/**
 * 接收本次证件的非空 OCR 结果，空字段自动补齐，已有内容保留候选供人工应用。
 * 日期由统一转换处理，行驶证正副页分别保留来源，不覆盖识别期间新增的手工内容。
 */
const buildInvoiceInfo = (data) => {
  receiveOcrCandidates('invoice', normalizeOcrCandidates(data, info.vehicleInvoice, 'vehicleInvoice'));

}

/**

 * * 企业车主上传营业执照后执行 OCR，保存文件关联并保留企业名称和统一社会信用代码候选。

 */
const handleBusinessLicenseChange = async (uploadFile, uploadFiles) => {
  const version = ocrUploadVersions.businessLicense = (ocrUploadVersions.businessLicense || 0) + 1;
  delete ocrResults.businessLicense;
  const validFiles = validFileSize(uploadFiles);
  file.businessLicense = validFiles;
  if (validFiles.length == 0){
    return;
  }
  try{
    loadingFlag.businessLicense = true;
    const { fileInfo, recognitionData, uploadFileItem } = await handleOcrFileUpload(uploadFile, "businessLicense");
    if (ocrUploadVersions.businessLicense !== version) return;
    workorderFileId.businessLicenseId = fileInfo.id;
    file.businessLicense = [uploadFileItem];
    buildBusinessLicenseInfo(recognitionData);
  }
  catch(error){
    if (ocrUploadVersions.businessLicense !== version) return;
    handleUploadFail('businessLicense', error.message || '图片上传失败，请重新上传');
  }
  finally{
    if (ocrUploadVersions.businessLicense === version) loadingFlag.businessLicense = false;
  }
}

/**
 * 接收本次证件的非空 OCR 结果，空字段自动补齐，已有内容保留候选供人工应用。
 * 日期由统一转换处理，行驶证正副页分别保留来源，不覆盖识别期间新增的手工内容。
 */
const buildBusinessLicenseInfo = (data) => {
  receiveOcrCandidates('businessLicense', normalizeOcrCandidates(data, { organizationName: 'organizationName', socialCreditCode: 'socialCreditCode' }));

}

/**
 * 按证件类型选择当前文件地址和业务化文件名，再交由统一下载工具保存。
 * 未找到对应上传文件时不触发下载，避免生成空白文件。
 */
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

/**

 * * 用户从上传控件移除证件时同步清除文件列表和请求体中的系统文件 ID。

 */
const handleRemove = (ElUploadFile, type) => {
  clearUploadState(type);
}

/**
 * 限制座位数、载重、整备质量、排量和发票金额等字段只能保留普通数字文本。
 * 非法输入会按字段类型清空对应值，避免提交时 Number 转换产生 NaN。
 */
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

/**

 * * 新增成功后缓存可处理模式和新工单 ID，并进入统一详情页继续报价、核保等后续阶段。

 */
const routeToDetail = (id) => {
  sessionStorage.setItem('workorderDetailType', 'handle');
  sessionStorage.setItem('workorderId', id);
  router.push({
    path: '/home/detailWorkorder',
  });
};

</script>


<style scoped>
/* 候选增加高度时，字段名仍对齐第一行输入框，不随整块内容垂直居中。 */
.cascade_param_item { align-items: flex-start; }
.cascade_form_title { line-height: 32px; align-self: flex-start; }
.cascade_form_content { min-width: 0; }
.cascade_form :deep(.el-form-item) { align-items: flex-start; }
.cascade_form :deep(.el-form-item__content) { align-content: flex-start; }
/* OCR复核区域使用无边框输入，保留原字段编辑和键盘操作。 */
.cascade_form :deep(.el-input__wrapper),
.cascade_form :deep(.el-input__wrapper:hover),
.cascade_form :deep(.el-input__wrapper.is-focus) {
  border: 0 !important;
  box-shadow: none !important;
  background-color: transparent !important;
  padding-left: 0;
  padding-right: 0;
}
.cascade_form :deep(.el-input__inner) { border: 0; }
/*
 * 批量应用放入识别区标题，按钮出现时不额外挤占正文行。
 * 身份证与营业执照固定两列表单轨道，候选只在各自输入区内增长高度，不按内容重新分列。
 */
.ocr-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; min-height: 32px; }
.ocr-section-heading > span { min-width: 0; }
.cascade_form_container > :deep(.el-form-item__label),
.cascade_form_container > :deep(.el-form-item__label-wrap) { width: 100%; height: auto; }
.cascade_form.el-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 24px; width: 100%; }
.cascade_form.el-form :deep(.el-form-item) { min-width: 0; width: 100%; margin: 0; }
.cascade_form :deep(.el-form-item__content) { min-width: 0; }
.cascade_form.el-form :deep(.el-form-item__content) { flex: 1; width: 0; }
.cascade_form :deep(.el-input) { min-width: 0; max-width: 100%; width: 100%; }
.ocr-global-apply { margin-left: auto; }
@media (max-width: 640px) {
  .cascade_form.el-form { grid-template-columns: minmax(0, 1fr); }
}
</style>
