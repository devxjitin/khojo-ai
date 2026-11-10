package com.khojoai.app.data

import android.content.Context
import com.khojoai.app.data.db.MyObjectBox
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.objectbox.BoxStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun boxStore(@ApplicationContext context: Context): BoxStore = MyObjectBox.builder().androidContext(context).build()
}
