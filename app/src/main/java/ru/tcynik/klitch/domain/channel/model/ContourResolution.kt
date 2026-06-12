package ru.tcynik.klitch.domain.channel.model

sealed interface ContourResolution {
    data class Deliver(val contour: Contour) : ContourResolution
    /** Emergency вне SOS: принять, но без UI-доставки */
    data class SilentStore(val contour: Contour) : ContourResolution
    data class Drop(val reason: String) : ContourResolution
}

fun ContourResolution.allowsDisplay(): Boolean = when (this) {
    is ContourResolution.Deliver -> true
    is ContourResolution.SilentStore -> false
    is ContourResolution.Drop -> false
}

fun ContourResolution.contourOrNull(): Contour? = when (this) {
    is ContourResolution.Deliver -> contour
    is ContourResolution.SilentStore -> contour
    is ContourResolution.Drop -> null
}
