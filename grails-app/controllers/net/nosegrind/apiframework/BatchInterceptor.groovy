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

import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Metadata
import groovy.json.JsonSlurper
import net.nosegrind.apiframework.comm.BatchRequestService
import net.nosegrind.apiframework.comm.BatchResponseService
import org.grails.web.util.WebUtils

import grails.artefact.controller.support.RequestForwarder
import org.grails.web.util.WebUtils

import javax.servlet.http.HttpServletResponse

//import net.nosegrind.apiframework.Timer

//@CompileStatic
class BatchInterceptor extends Params{

	int order = HIGHEST_PRECEDENCE + 999

	GrailsApplication grailsApplication
	BatchRequestService batchRequestService
	BatchResponseService batchResponseService
	ApiCacheService apiCacheService
	SpringSecurityService springSecurityService

	String entryPoint = "b${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"


	BatchInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before(){
		//println("##### BATCHINTERCEPTOR (BEFORE)")

		Map methods = ['GET':'show','PUT':'update','POST':'create','DELETE':'delete']

		// Init params
		String format =request.format.toUpperCase()

		if(['XML','JSON'].contains(format)) {
			LinkedHashMap dataParams = [:]
			switch (format) {
				case 'XML':

					String xml = request."${request.getAttribute('format')}".toString()
					def slurper = new XmlSlurper()
					slurper.parseText(xml).each() { k, v ->
						dataParams[k] = v
					}
					request.setAttribute("${format}", dataParams)
					break
				case 'JSON':
					String json = request."${format}".toString()
					def slurper = new JsonSlurper()
					slurper.parseText(json).each() { k, v ->
						dataParams[k] = v
					}
					request.setAttribute("${format}", dataParams)
					break
			}
		}


		try{
			//if(request.class.toString().contains('SecurityContextHolderAwareRequestWrapper')){

			LinkedHashMap cache = (params.controller)? apiCacheService.getApiCache(params.controller.toString()):[:]
			if(!params.action){
				params.action = cache['defaultAction']
			}
				if(cache) {
					params.apiObject = (params.apiObjectVersion)?params.apiObjectVersion:cache['currentStable']['value']

					if (request.getAttribute('batchInc')==null) {
						request.setAttribute('batchInc',0)
					}else{
						request.setAttribute('batchInc',request.getAttribute('batchInc').toInteger() + 1)
					}

					setBatchParams(params)

					LinkedHashMap receives = cache[params.apiObject.toString()][params.action.toString()]['receives'] as LinkedHashMap
					boolean requestKeysMatch = checkURIDefinitions(cache[params.apiObject.toString()][params.action.toString()]['method'] as String,params,receives)

					if(!requestKeysMatch){
						render(status:HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables do not match sent variables')
						return false
					}

					if(!params.action){
						String methodAction = methods[request.method]
						if(!cache[params.apiObject][methodAction]){
							params.action = cache[params.apiObject]['defaultAction']
						}else{
							params.action = methods[request.method]

							// FORWARD FOR REST DEFAULTS WITH NO ACTION
							String[] tempUri = request.getRequestURI().split("/")
							if(tempUri[2].contains('dispatch') && "${params.controller}.dispatch" == tempUri[2] && !cache[params.apiObject]['domainPackage']){
								forward(controller:params.controller,action:params.action,params:params)
								return false
							}
						}
					}

					// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
					boolean result = batchRequestService.handleApiRequest(cache[params.apiObject.toString()][params.action.toString()], request, response, params)
					return result
				}
			//}
			return false

		}catch(Exception e) {
			log.error("[ApiToolkitFilters :: preHandler] : Exception - full stack trace follows:", e)
			return false
		}
	}

	boolean after(){
		//println("##### BATCHFILTER (AFTER)")
		try{
			LinkedHashMap newModel = [:]

			if (!model) {
				render(status:HttpServletResponse.SC_NOT_FOUND , text: 'No resource returned')
				return false
			} else {
				newModel = batchResponseService.convertModel(model)
			}

			LinkedHashMap cache = apiCacheService.getApiCache(params.controller.toString())
			LinkedHashMap content

			if(batchEnabled && (request.getAttribute('batchLength')>(request.getAttribute('batchInc')+1))){
				WebUtils.exposeRequestAttributes(request, params);
				// this will work fine when we upgrade to newer version that has fix in iut
				params.uri = request.forwardURI.toString()
				forward(params)
				return false
			}

			content = batchResponseService.handleApiResponse(cache[params.apiObject][params.action.toString()],request,response,newModel,params) as LinkedHashMap

			if(content){
				render(text:content.apiToolkitContent, contentType:"${content.apiToolkitType}", encoding:content.apiToolkitEncoding)
				return false
			}

			return false
		}catch(Exception e){
			log.error("[ApiToolkitFilters :: apitoolkit.after] : Exception - full stack trace follows:", e);
			return false
		}

	}

}
