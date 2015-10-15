package com.company

import com.company.subpackage1.*
import com.company2.packageother.OtherHelper
import com.other.package10.OtherScript

println "111 22 33 other script:" + OtherScript.generate() + " OTHER:" + OtherHelper.doOtherMethod() + "Date 1:|" + ScriptHelper2.getTime() + "| sublevel1:" + Subpackage1Class.method1() + "|" + Thread.currentThread().getName() + "|" + new Date().getTime()
//println "OTHER:" + OtherHelper.doOtherMethod() + "Date 1:|" + ScriptHelper2.getTime() + "| sublevel1:" + Subpackage1Class.method1() + "|" + Thread.currentThread().getName() + "|" + new Date().getTime()
//println "Date 1:|" + ScriptHelper2.getTime() + "| sublevel1:" + Subpackage1Class.method1() + "|" + Thread.currentThread().getName() + "|" + new Date().getTime()
