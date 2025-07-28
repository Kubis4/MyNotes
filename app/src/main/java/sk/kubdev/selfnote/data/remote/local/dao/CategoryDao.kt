package sk.kubdev.selfnote.data.remote.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sk.kubdev.selfnote.data.remote.local.entities.Category

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories ORDER BY `order` ASC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Int): Category?

    @Query("UPDATE categories SET `order` = :order WHERE id = :id")
    suspend fun updateCategoryOrder(id: Int, order: Int)
}
