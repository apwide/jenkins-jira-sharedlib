package com.apwide.jenkins.util

import static com.apwide.jenkins.util.JsonMarshaller.toJsonText

class RestClient implements Serializable {
    private final ScriptWrapper script
    private final Map config
    private final String resourceUrl

    RestClient(script, Map config, String resourceUrl = '') {
        this.script = script
        this.config = config
        this.resourceUrl = resourceUrl
    }

    private def request(httpMode = 'GET', path = '', body = null, validResponseCodes = '200:304') {
        if (config.goliveCloudCredentialsId) {
            script.withCredentials([script.string(credentialsId: config.goliveCloudCredentialsId, variable: 'APW_INTERNAL_GOLIVE_CLOUD_CREDENTIALS_ID')]) {
                def token = this.script.env("APW_INTERNAL_GOLIVE_CLOUD_CREDENTIALS_ID")
                return executeRequest(httpMode, path, body, validResponseCodes, token)
            }
        } else {
            return executeRequest(httpMode, path, body, validResponseCodes)
        }
    }

    private def executeRequest(httpMode = 'GET', path = '', body = null, validResponseCodes = '200:304', String goliveCloudToken = null) {
        def previousResult = script.getCurrentBuildResult()
        def url = "${resourceUrl}${path}"
        def authentication = goliveCloudToken ? null : config.jiraCredentialsId
        def authenticationHeaders = goliveCloudToken ? [[name: 'api-key', value: goliveCloudToken, markValue: true]] : null
        try {
            def response = script.httpRequest(
                    authentication: authentication,
                    customHeaders: authenticationHeaders,
                    consoleLogResponseBody: true,
                    timeout: 5,
                    httpMode: httpMode,
                    requestBody: toJsonText(body),
                    contentType: 'APPLICATION_JSON',
                    url: url,
                    quiet: !script.isLogEnabled(),
                    validResponseCodes: validResponseCodes)
            return response.content ? script.readJSON(text: response.content) : null
        } catch (err) {
            script.debug("Error during Rest call: ${err}")
            script.debug("Url: ${httpMode} ${url}")
            script.debug("Body: ${body}")
            if (config.buildFailOnError) {
                script.debug("Build marked to fail")
                throw err
            }
            script.debug("Build marked to not fail")
            script.debug("Previous build result ${previousResult}")
            script.setCurrentBuildResult(previousResult)
            return null
        }
    }

    def put(path = '', body = null) {
        return request('PUT', path, body)
    }

    def post(path = '', body = null) {
        return request('POST', path, body)
    }

    def get(path = '', validResponseCodes  = '200:304') {
        return request('GET', path, null, validResponseCodes)
    }

    def delete(path = '') {
        return request('DELETE', path)
    }

    static def checkUrl(Map params, ScriptWrapper script) {
        int tries = 0
        int nbRetry = params.nbRetry ?: 1

        while (tries <= nbRetry) {
            try {
                script.httpRequest(params)
                return
            } catch (err) {
                if (tries >= nbRetry) {
                    throw err
                }
            }
            tries++
        }

    }
}
