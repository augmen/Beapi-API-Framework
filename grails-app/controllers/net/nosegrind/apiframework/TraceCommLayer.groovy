/*
 * The MIT License (MIT)
 * Copyright 2014 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright/trademark notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nosegrind.apiframework


import javax.servlet.http.HttpServletRequest
import grails.web.servlet.mvc.GrailsParameterMap
import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic

// extended by Intercepters
@CompileStatic
abstract class TraceCommLayer extends TraceCommProcess{



    /***************************
     * REQUESTS
     ***************************/
    boolean handleApiRequest(ApiDescriptor cache, HttpServletRequest request, HttpServletResponse response, GrailsParameterMap params){
        traceService.startTrace('TraceCommLayer','handleApiRequest')
        try{
            // CHECK ACCESS TO METHOD
            List roles = cache['roles'] as List
            if(!checkAuth(request,roles)){
                response.status = 401
                response.setHeader('ERROR','Unauthorized Access attempted')
                return false
            }

            // CHECK VERSION DEPRECATION DATE
            List deprecated = cache['deprecated'] as List

            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    String depMsg = deprecated[1].toString()
                    response.status = 400
                    response.setHeader('ERROR',depMsg)
                    traceService.endTrace('TraceCommLayer','handleApiRequest')
                    return false
                }
            }

            def method = cache['method']?.toString().trim()

            // DOES api.methods.contains(request.method)
            if(!isRequestMatch(method,request.method.toString())){
                response.status = 400
                response.setHeader('ERROR',"Request method doesn't match expected method.")
                traceService.endTrace('TraceCommLayer','handleApiRequest')
                return false
            }
            traceService.endTrace('TraceCommLayer','handleApiRequest')
            return true
        }catch(Exception e){
            traceService.endTrace('TraceCommLayer','handleApiRequest')
            throw new Exception("[ApiCommLayer : handleApiRequest] : Exception - full stack trace follows:",e)
        }
    }

    /***************************
    * RESPONSES
     ***************************/
    def handleApiResponse(ApiDescriptor cache, HttpServletRequest request, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        traceService.startTrace('TraceCommLayer','handleApiResponse')
        try{
            response.setHeader('Authorization', cache['roles'].toString().join(', '))
            List responseList = getApiParams((LinkedHashMap)cache['returns'])
            LinkedHashMap content = [:]
            if(params.controller!='apidoc') {
                LinkedHashMap result = parseURIDefinitions(model, responseList)

                // will parse empty map the same as map with content
                content = parseResponseMethod(request, params, result)
            }else{
                content = parseResponseMethod(request, params, model)
            }
            traceService.endTrace('TraceCommLayer','handleApiResponse')
            return content

        }catch(Exception e){
            traceService.endTrace('TraceCommLayer','handleApiResponse')
            throw new Exception("[ApiCommLayer : handleApiResponse] : Exception - full stack trace follows:",e)
        }
    }


}