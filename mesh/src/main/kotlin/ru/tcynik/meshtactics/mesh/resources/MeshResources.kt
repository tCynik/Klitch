/*
 * Bridge replacing Compose Multiplatform resources with standard Android string resources.
 * Provides the same API surface (Res.string.xxx, getString, getStringSuspend) so that
 * copied code from the KMP Meshtastic project compiles unchanged.
 */
package ru.tcynik.meshtactics.mesh.resources

import ru.tcynik.meshtactics.mesh.R
import ru.tcynik.meshtactics.mesh.common.ContextServices

/**
 * Android resource ID standing in for the KMP StringResource type.
 * Declared here so files that previously imported
 * `org.jetbrains.compose.resources.StringResource` can instead import this one.
 */
typealias StringResource = Int

/** Top-level resource accessor — mirrors the generated Compose `Res` object. */
object Res {
    object string {
        val client_notification get() = R.string.client_notification
        val connected get() = R.string.connected
        val connecting get() = R.string.connecting
        val disconnected get() = R.string.disconnected
        val device_sleeping get() = R.string.device_sleeping
        val meshtastic_app_name get() = R.string.meshtastic_app_name
        val meshtastic_service_notifications get() = R.string.meshtastic_service_notifications
        val meshtastic_messages_notifications get() = R.string.meshtastic_messages_notifications
        val meshtastic_broadcast_notifications get() = R.string.meshtastic_broadcast_notifications
        val meshtastic_alerts_notifications get() = R.string.meshtastic_alerts_notifications
        val meshtastic_new_nodes_notifications get() = R.string.meshtastic_new_nodes_notifications
        val meshtastic_waypoints_notifications get() = R.string.meshtastic_waypoints_notifications
        val meshtastic_low_battery_notifications get() = R.string.meshtastic_low_battery_notifications
        val meshtastic_low_battery_temporary_remote_notifications get() = R.string.meshtastic_low_battery_temporary_remote_notifications
        val mark_as_read get() = R.string.mark_as_read
        val reply get() = R.string.reply
        val error get() = R.string.error
        val unrecognized get() = R.string.unrecognized
        val you get() = R.string.you
        val powered get() = R.string.powered
        val unknown_username get() = R.string.unknown_username
        val no_local_stats get() = R.string.no_local_stats
        val critical_alert get() = R.string.critical_alert
        val new_node_seen get() = R.string.new_node_seen
        val waypoint_received get() = R.string.waypoint_received
        val error_duty_cycle get() = R.string.error_duty_cycle
        val low_battery_message get() = R.string.low_battery_message
        val low_battery_title get() = R.string.low_battery_title
        val message_delivery_status get() = R.string.message_delivery_status
        val delivery_confirmed get() = R.string.delivery_confirmed
        val message_status_enroute get() = R.string.message_status_enroute
        val message_status_queued get() = R.string.message_status_queued
        val message_status_sfpp_routing get() = R.string.message_status_sfpp_routing
        val message_status_sfpp_confirmed get() = R.string.message_status_sfpp_confirmed
        val routing_error_none get() = R.string.routing_error_none
        val routing_error_no_route get() = R.string.routing_error_no_route
        val routing_error_got_nak get() = R.string.routing_error_got_nak
        val routing_error_timeout get() = R.string.routing_error_timeout
        val routing_error_no_interface get() = R.string.routing_error_no_interface
        val routing_error_max_retransmit get() = R.string.routing_error_max_retransmit
        val routing_error_no_channel get() = R.string.routing_error_no_channel
        val routing_error_too_large get() = R.string.routing_error_too_large
        val routing_error_no_response get() = R.string.routing_error_no_response
        val routing_error_bad_request get() = R.string.routing_error_bad_request
        val routing_error_duty_cycle_limit get() = R.string.routing_error_duty_cycle_limit
        val routing_error_not_authorized get() = R.string.routing_error_not_authorized
        val routing_error_pki_failed get() = R.string.routing_error_pki_failed
        val routing_error_pki_unknown_pubkey get() = R.string.routing_error_pki_unknown_pubkey
        val routing_error_admin_bad_session_key get() = R.string.routing_error_admin_bad_session_key
        val routing_error_admin_public_key_unauthorized get() = R.string.routing_error_admin_public_key_unauthorized
        val routing_error_pki_send_fail_public_key get() = R.string.routing_error_pki_send_fail_public_key
        val routing_error_rate_limit_exceeded get() = R.string.routing_error_rate_limit_exceeded
        val local_stats_battery get() = R.string.local_stats_battery
        val local_stats_nodes get() = R.string.local_stats_nodes
        val local_stats_uptime get() = R.string.local_stats_uptime
        val local_stats_utilization get() = R.string.local_stats_utilization
        val local_stats_traffic get() = R.string.local_stats_traffic
        val local_stats_relays get() = R.string.local_stats_relays
        val local_stats_diagnostics_prefix get() = R.string.local_stats_diagnostics_prefix
        val local_stats_noise get() = R.string.local_stats_noise
        val local_stats_bad get() = R.string.local_stats_bad
        val local_stats_dropped get() = R.string.local_stats_dropped
        val local_stats_heap get() = R.string.local_stats_heap
        val local_stats_heap_value get() = R.string.local_stats_heap_value
        val node_sort_alpha get() = R.string.node_sort_alpha
        val node_sort_channel get() = R.string.node_sort_channel
        val node_sort_distance get() = R.string.node_sort_distance
        val node_sort_hops_away get() = R.string.node_sort_hops_away
        val node_sort_last_heard get() = R.string.node_sort_last_heard
        val node_sort_via_mqtt get() = R.string.node_sort_via_mqtt
        val node_sort_via_favorite get() = R.string.node_sort_via_favorite
        val tak_team_unspecified_color get() = R.string.tak_team_unspecified_color
        val tak_team_white get() = R.string.tak_team_white
        val tak_team_yellow get() = R.string.tak_team_yellow
        val tak_team_orange get() = R.string.tak_team_orange
        val tak_team_magenta get() = R.string.tak_team_magenta
        val tak_team_red get() = R.string.tak_team_red
        val tak_team_maroon get() = R.string.tak_team_maroon
        val tak_team_purple get() = R.string.tak_team_purple
        val tak_team_dark_blue get() = R.string.tak_team_dark_blue
        val tak_team_blue get() = R.string.tak_team_blue
        val tak_team_cyan get() = R.string.tak_team_cyan
        val tak_team_teal get() = R.string.tak_team_teal
        val tak_team_green get() = R.string.tak_team_green
        val tak_team_dark_green get() = R.string.tak_team_dark_green
        val tak_team_brown get() = R.string.tak_team_brown
        val tak_role_unspecified get() = R.string.tak_role_unspecified
        val tak_role_teammember get() = R.string.tak_role_teammember
        val tak_role_teamlead get() = R.string.tak_role_teamlead
        val tak_role_hq get() = R.string.tak_role_hq
        val tak_role_sniper get() = R.string.tak_role_sniper
        val tak_role_medic get() = R.string.tak_role_medic
        val tak_role_forwardobserver get() = R.string.tak_role_forwardobserver
        val tak_role_rto get() = R.string.tak_role_rto
        val tak_role_k9 get() = R.string.tak_role_k9
    }
}

// Top-level shorthands — imported individually by the copied files
val client_notification: StringResource get() = Res.string.client_notification
val connected: StringResource get() = Res.string.connected
val connecting: StringResource get() = Res.string.connecting
val disconnected: StringResource get() = Res.string.disconnected
val device_sleeping: StringResource get() = Res.string.device_sleeping
val meshtastic_app_name: StringResource get() = Res.string.meshtastic_app_name
val meshtastic_service_notifications: StringResource get() = Res.string.meshtastic_service_notifications
val meshtastic_messages_notifications: StringResource get() = Res.string.meshtastic_messages_notifications
val meshtastic_broadcast_notifications: StringResource get() = Res.string.meshtastic_broadcast_notifications
val meshtastic_alerts_notifications: StringResource get() = Res.string.meshtastic_alerts_notifications
val meshtastic_new_nodes_notifications: StringResource get() = Res.string.meshtastic_new_nodes_notifications
val meshtastic_waypoints_notifications: StringResource get() = Res.string.meshtastic_waypoints_notifications
val meshtastic_low_battery_notifications: StringResource get() = Res.string.meshtastic_low_battery_notifications
val meshtastic_low_battery_temporary_remote_notifications: StringResource get() = Res.string.meshtastic_low_battery_temporary_remote_notifications
val mark_as_read: StringResource get() = Res.string.mark_as_read
val reply: StringResource get() = Res.string.reply
val error: StringResource get() = Res.string.error
val unrecognized: StringResource get() = Res.string.unrecognized
val you: StringResource get() = Res.string.you
val powered: StringResource get() = Res.string.powered
val unknown_username: StringResource get() = Res.string.unknown_username
val no_local_stats: StringResource get() = Res.string.no_local_stats
val critical_alert: StringResource get() = Res.string.critical_alert
val new_node_seen: StringResource get() = Res.string.new_node_seen
val waypoint_received: StringResource get() = Res.string.waypoint_received
val error_duty_cycle: StringResource get() = Res.string.error_duty_cycle
val low_battery_message: StringResource get() = Res.string.low_battery_message
val low_battery_title: StringResource get() = Res.string.low_battery_title
val message_delivery_status: StringResource get() = Res.string.message_delivery_status
val delivery_confirmed: StringResource get() = Res.string.delivery_confirmed
val message_status_enroute: StringResource get() = Res.string.message_status_enroute
val message_status_queued: StringResource get() = Res.string.message_status_queued
val message_status_sfpp_routing: StringResource get() = Res.string.message_status_sfpp_routing
val message_status_sfpp_confirmed: StringResource get() = Res.string.message_status_sfpp_confirmed
val routing_error_none: StringResource get() = Res.string.routing_error_none
val routing_error_no_route: StringResource get() = Res.string.routing_error_no_route
val routing_error_got_nak: StringResource get() = Res.string.routing_error_got_nak
val routing_error_timeout: StringResource get() = Res.string.routing_error_timeout
val routing_error_no_interface: StringResource get() = Res.string.routing_error_no_interface
val routing_error_max_retransmit: StringResource get() = Res.string.routing_error_max_retransmit
val routing_error_no_channel: StringResource get() = Res.string.routing_error_no_channel
val routing_error_too_large: StringResource get() = Res.string.routing_error_too_large
val routing_error_no_response: StringResource get() = Res.string.routing_error_no_response
val routing_error_bad_request: StringResource get() = Res.string.routing_error_bad_request
val routing_error_duty_cycle_limit: StringResource get() = Res.string.routing_error_duty_cycle_limit
val routing_error_not_authorized: StringResource get() = Res.string.routing_error_not_authorized
val routing_error_pki_failed: StringResource get() = Res.string.routing_error_pki_failed
val routing_error_pki_unknown_pubkey: StringResource get() = Res.string.routing_error_pki_unknown_pubkey
val routing_error_admin_bad_session_key: StringResource get() = Res.string.routing_error_admin_bad_session_key
val routing_error_admin_public_key_unauthorized: StringResource get() = Res.string.routing_error_admin_public_key_unauthorized
val routing_error_pki_send_fail_public_key: StringResource get() = Res.string.routing_error_pki_send_fail_public_key
val routing_error_rate_limit_exceeded: StringResource get() = Res.string.routing_error_rate_limit_exceeded
val local_stats_battery: StringResource get() = Res.string.local_stats_battery
val local_stats_nodes: StringResource get() = Res.string.local_stats_nodes
val local_stats_uptime: StringResource get() = Res.string.local_stats_uptime
val local_stats_utilization: StringResource get() = Res.string.local_stats_utilization
val local_stats_traffic: StringResource get() = Res.string.local_stats_traffic
val local_stats_relays: StringResource get() = Res.string.local_stats_relays
val local_stats_diagnostics_prefix: StringResource get() = Res.string.local_stats_diagnostics_prefix
val local_stats_noise: StringResource get() = Res.string.local_stats_noise
val local_stats_bad: StringResource get() = Res.string.local_stats_bad
val local_stats_dropped: StringResource get() = Res.string.local_stats_dropped
val local_stats_heap: StringResource get() = Res.string.local_stats_heap
val local_stats_heap_value: StringResource get() = Res.string.local_stats_heap_value
val node_sort_alpha: StringResource get() = Res.string.node_sort_alpha
val node_sort_channel: StringResource get() = Res.string.node_sort_channel
val node_sort_distance: StringResource get() = Res.string.node_sort_distance
val node_sort_hops_away: StringResource get() = Res.string.node_sort_hops_away
val node_sort_last_heard: StringResource get() = Res.string.node_sort_last_heard
val node_sort_via_mqtt: StringResource get() = Res.string.node_sort_via_mqtt
val node_sort_via_favorite: StringResource get() = Res.string.node_sort_via_favorite
val tak_team_unspecified_color: StringResource get() = Res.string.tak_team_unspecified_color
val tak_team_white: StringResource get() = Res.string.tak_team_white
val tak_team_yellow: StringResource get() = Res.string.tak_team_yellow
val tak_team_orange: StringResource get() = Res.string.tak_team_orange
val tak_team_magenta: StringResource get() = Res.string.tak_team_magenta
val tak_team_red: StringResource get() = Res.string.tak_team_red
val tak_team_maroon: StringResource get() = Res.string.tak_team_maroon
val tak_team_purple: StringResource get() = Res.string.tak_team_purple
val tak_team_dark_blue: StringResource get() = Res.string.tak_team_dark_blue
val tak_team_blue: StringResource get() = Res.string.tak_team_blue
val tak_team_cyan: StringResource get() = Res.string.tak_team_cyan
val tak_team_teal: StringResource get() = Res.string.tak_team_teal
val tak_team_green: StringResource get() = Res.string.tak_team_green
val tak_team_dark_green: StringResource get() = Res.string.tak_team_dark_green
val tak_team_brown: StringResource get() = Res.string.tak_team_brown
val tak_role_unspecified: StringResource get() = Res.string.tak_role_unspecified
val tak_role_teammember: StringResource get() = Res.string.tak_role_teammember
val tak_role_teamlead: StringResource get() = Res.string.tak_role_teamlead
val tak_role_hq: StringResource get() = Res.string.tak_role_hq
val tak_role_sniper: StringResource get() = Res.string.tak_role_sniper
val tak_role_medic: StringResource get() = Res.string.tak_role_medic
val tak_role_forwardobserver: StringResource get() = Res.string.tak_role_forwardobserver
val tak_role_rto: StringResource get() = Res.string.tak_role_rto
val tak_role_k9 get() = Res.string.tak_role_k9

/** Retrieve a string by its Android resource ID. */
fun getString(stringResource: StringResource): String =
    ContextServices.app.getString(stringResource)

/** Retrieve a formatted string by its Android resource ID. */
fun getString(stringResource: StringResource, vararg formatArgs: Any): String =
    ContextServices.app.getString(stringResource, *formatArgs)

/** Suspend variant — backed by the same Android getString (no coroutine needed on Android). */
suspend fun getStringSuspend(stringResource: StringResource): String =
    ContextServices.app.getString(stringResource)

/** Suspend variant with format args. */
suspend fun getStringSuspend(stringResource: StringResource, vararg formatArgs: Any): String =
    ContextServices.app.getString(stringResource, *formatArgs)
