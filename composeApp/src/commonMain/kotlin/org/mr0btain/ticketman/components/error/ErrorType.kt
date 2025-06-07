package org.mr0btain.ticketman.components.error

sealed class ErrorType {

    sealed class Api: ErrorType() {

        object Network: Api()

        object ServiceUnavailable : Api()

        object NotFound : Api()

        object Server : Api()

    }

    sealed class Data: ErrorType() {
        object Convert: Data()
        object Transfer: Data()
    }

    object Unknown: ErrorType()

    // other categories of Error
}

//class MyException(val myMessage: String, val statusCode: Int, val cause: String) : Exception(myMessage)