package ru.tcynik.meshtactics.presentation.feature.marks.models

enum class GeoMarkDeliveryFilterStatus {
    /** Меток этого типа нет — кнопка неактивна. */
    INACTIVE,
    /** Фильтр включён — метки типа видны в списке. */
    SELECTED,
    /** Метки типа есть, но скрыты фильтром. */
    UNSELECTED,
}
