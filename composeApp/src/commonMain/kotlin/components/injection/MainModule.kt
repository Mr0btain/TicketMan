package components.injection

import components.MainViewModel
import org.koin.dsl.module

val mainModule = module {
    factory { MainViewModel() }
}