package ru.tcynik.mymesh1.`data`.local

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class NodeQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectAll(mapper: (
    id: String,
    name: String,
    address: String,
    rssi: Long,
    is_connected: Long,
    last_seen: Long,
  ) -> T): Query<T> = Query(-1_176_286_987, arrayOf("Node"), driver, "Node.sq", "selectAll",
      "SELECT Node.id, Node.name, Node.address, Node.rssi, Node.is_connected, Node.last_seen FROM Node") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun selectAll(): Query<Node> = selectAll { id, name, address, rssi, is_connected,
      last_seen ->
    Node(
      id,
      name,
      address,
      rssi,
      is_connected,
      last_seen
    )
  }

  public fun <T : Any> selectById(id: String, mapper: (
    id: String,
    name: String,
    address: String,
    rssi: Long,
    is_connected: Long,
    last_seen: Long,
  ) -> T): Query<T> = SelectByIdQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun selectById(id: String): Query<Node> = selectById(id) { id_, name, address, rssi,
      is_connected, last_seen ->
    Node(
      id_,
      name,
      address,
      rssi,
      is_connected,
      last_seen
    )
  }

  public fun insertOrReplace(
    id: String,
    name: String,
    address: String,
    rssi: Long,
    isConnected: Long,
    lastSeen: Long,
  ) {
    driver.execute(1_882_390_152, """
        |INSERT OR REPLACE INTO Node(id, name, address, rssi, is_connected, last_seen)
        |VALUES (?, ?, ?, ?, ?, ?)
        """.trimMargin(), 6) {
          bindString(0, id)
          bindString(1, name)
          bindString(2, address)
          bindLong(3, rssi)
          bindLong(4, isConnected)
          bindLong(5, lastSeen)
        }
    notifyQueries(1_882_390_152) { emit ->
      emit("Node")
    }
  }

  public fun deleteAll() {
    driver.execute(180_979_174, """DELETE FROM Node""", 0)
    notifyQueries(180_979_174) { emit ->
      emit("Node")
    }
  }

  public fun deleteById(id: String) {
    driver.execute(1_315_428_397, """DELETE FROM Node WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(1_315_428_397) { emit ->
      emit("Node")
    }
  }

  private inner class SelectByIdQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("Node", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("Node", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-2_105_116_930,
        """SELECT Node.id, Node.name, Node.address, Node.rssi, Node.is_connected, Node.last_seen FROM Node WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "Node.sq:selectById"
  }
}
