package ru.tcynik.mymesh1.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.tcynik.mymesh1.presentation.feature.nodes.NodesViewModel

val presentationModule = module {
    viewModel { NodesViewModel(get()) }
}
