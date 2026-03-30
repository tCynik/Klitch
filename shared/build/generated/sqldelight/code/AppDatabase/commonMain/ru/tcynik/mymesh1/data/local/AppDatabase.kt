package ru.tcynik.mymesh1.`data`.local

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlin.Unit
import ru.tcynik.mymesh1.`data`.local.shared.newInstance
import ru.tcynik.mymesh1.`data`.local.shared.schema

public interface AppDatabase : Transacter {
  public val nodeQueries: NodeQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = AppDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): AppDatabase =
        AppDatabase::class.newInstance(driver)
  }
}
