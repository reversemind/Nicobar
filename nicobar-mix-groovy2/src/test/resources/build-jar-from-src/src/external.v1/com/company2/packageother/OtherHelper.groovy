package com.company2.packageother

class OtherHelper {

    def static String doOtherMethod() {
        return " FROM OTHER METHOD " + this.getClass().getCanonicalName()
    }

}
