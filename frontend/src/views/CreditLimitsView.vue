<template>
  <div class="page">
    <h2 class="page-title">会员额度</h2>
    <p class="page-desc">按会员 + 币种设置义务额度上限;超过上限的新建义务将被拦截（仅约束新建义务）</p>

    <div class="toolbar">
      <el-button type="primary" :disabled="!auth.isOperator" @click="openCreate">设置额度</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="card-panel">
      <el-table :data="limits" v-loading="loading" stripe>
        <el-table-column prop="memberName" label="会员" min-width="160" />
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column label="额度上限" min-width="150">
          <template #default="{ row }">{{ fmt(row.limitAmount) }}</template>
        </el-table-column>
        <el-table-column label="已用（OPEN）" min-width="150">
          <template #default="{ row }">{{ fmt(row.usedAmount) }}</template>
        </el-table-column>
        <el-table-column label="可用余额" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'over-text': remaining(row) < 0 }">{{ fmt(remaining(row)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="使用率" width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="usagePercent(row)"
              :status="usagePercent(row) >= 100 ? 'exception' : usagePercent(row) >= 80 ? 'warning' : ''"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="remaining(row) < 0 ? 'danger' : 'success'">
              {{ remaining(row) < 0 ? '已超限' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" :disabled="!auth.isOperator" @click="openEdit(row)">修改</el-button>
            <el-button size="small" type="danger" :disabled="!auth.isOperator" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span style="color: var(--muted)">暂无额度记录,点击「设置额度」为会员添加上限</span>
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dialog" :title="editing ? '修改额度' : '设置额度'" width="440px">
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="会员">
          <el-select
            v-model="form.memberId"
            filterable
            style="width: 100%"
            :disabled="editing"
            placeholder="选择会员"
          >
            <el-option v-for="m in members" :key="m.memberId" :label="m.name" :value="m.memberId" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="form.currency" style="width: 100%" :disabled="editing" placeholder="选择币种">
            <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="额度上限">
          <el-input-number
            v-model="form.limitAmount"
            :min="0.00000001"
            :precision="8"
            :controls="false"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const members = ref([])
const limits = ref([])
const loading = ref(false)
const saving = ref(false)
const dialog = ref(false)
const editing = ref(false)
const currencies = ['USD', 'CNY', 'EUR']

const form = reactive({ memberId: '', currency: 'USD', limitAmount: 1000000 })

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: 8 })
}

function remaining(row) {
  return Number(row.limitAmount) - Number(row.usedAmount || 0)
}

function usagePercent(row) {
  const limit = Number(row.limitAmount)
  if (!limit) return 0
  return Math.min(999, Math.round((Number(row.usedAmount || 0) / limit) * 100))
}

async function load() {
  loading.value = true
  try {
    const [m, l] = await Promise.all([api.get('/members'), api.get('/members/credit-limits')])
    members.value = m.data
    limits.value = l.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  form.memberId = members.value[0]?.memberId || ''
  form.currency = 'USD'
  form.limitAmount = 1000000
  dialog.value = true
}

function openEdit(row) {
  editing.value = true
  form.memberId = row.memberId
  form.currency = row.currency
  form.limitAmount = Number(row.limitAmount)
  dialog.value = true
}

async function save() {
  if (!form.memberId) {
    ElMessage.warning('请选择会员')
    return
  }
  if (!form.currency) {
    ElMessage.warning('请选择币种')
    return
  }
  if (!form.limitAmount || form.limitAmount <= 0) {
    ElMessage.warning('额度上限必须为正数')
    return
  }
  saving.value = true
  try {
    await api.put('/members/credit-limits', {
      memberId: form.memberId,
      currency: form.currency,
      limitAmount: form.limitAmount
    })
    ElMessage.success('额度已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除 ${row.memberName} 的 ${row.currency} 额度上限吗?删除后不再限制。`, '删除额度', {
      type: 'warning'
    })
  } catch {
    return
  }
  await api.delete('/members/credit-limits', { params: { memberId: row.memberId, currency: row.currency } })
  ElMessage.success('额度已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.over-text {
  color: #e5534b;
  font-weight: 600;
}
</style>
