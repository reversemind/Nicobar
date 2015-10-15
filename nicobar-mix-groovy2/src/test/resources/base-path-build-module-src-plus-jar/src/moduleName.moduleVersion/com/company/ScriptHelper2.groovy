package com.company

class ScriptHelper2 {

    def static String getTime() {
        return " script helper 2:" + new Date()
    }

    def static String getResponse(String string) {
        return "ScriptHelper2|" + string
    }
}
