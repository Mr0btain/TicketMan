package components.injection

import components.MainViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    // New way using viewModelOf
    viewModelOf(::MainViewModel)
}