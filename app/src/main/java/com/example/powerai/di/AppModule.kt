package com.example.powerai.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * AppModule 已按功能域拆分为多个模块以提高可维护性：
 * 
 * - [CoreModule]: 核心基础设施（Context、Gson、WorkManager、Dispatcher 等）
 * - [DatabaseModule]: 数据库和 DAO
 * - [NetworkModule]: HTTP 客户端和 API 服务
 * - [DataModule]: 数据仓库和导入器
 * - [AIModule]: AI 推理、检索和向量搜索服务
 * - [RepositoryModule]: Repository 接口@Binds 绑定（已存在
 * 
 * 此文件保留作为历史标记，实际依赖注入已迁移到上述模块
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // 所有依赖注入已迁移到功能域模块
    // 保留此空模块以避免破坏现有代码引
}
