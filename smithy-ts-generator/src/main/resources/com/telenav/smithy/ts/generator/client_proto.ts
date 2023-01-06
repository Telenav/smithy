
// Needed when testing in node:
//import { XMLHttpRequest } from 'xmlhttprequest'

export type JSONConverter = <T>(rawObject: object) => T;

export type RequestConfigurer = (xhr: XMLHttpRequest) => void;

export type ClientEvent = 'load' | 'error' | 'timeout' | 'abort'
    | 'loadstart' | 'loadend' | 'progress' | 'readystatechange';

/**
 * Allows the client to be monitored for activity and state changes - 
 * pass a ServiceClientListener to `ServiceClient.listen()`.
 */
export type ServiceClientListener = (requestId: number, msg: ClientEvent,
    running: number, request?: ServiceRequest, event?: any) => void;

/**
 * Function type returned by `ServiceClient.listen()` which removes
 * the listener passed in that call, and returns true if the listener
 * was not already removed.
 */
export type ListenerRemover = () => boolean;

/**
 * A query passed to one of the request methods of ServiceClient.
 */
export interface Query {
    /**
     * The request uri.
     */
    uri: string,
    /**
     * Optional query parameters.
     */
    queryParams?: object,
    /**
     * Optional headers.
     */
    headers?: object,
    /**
     * If true, pass `Cache-Control: no-cache` as a header.
     */
    noCache?: boolean,
    /**
     * Optional request timeout.
     */
    timeout?: number
}

/**
 * Request object immediately returned when issuing a request to
 * the service client, which allows for cancellation of the request
 * and provides an identifier for it.
 * Use `result()` to convert this to a promise which will be passed
 * a type object converted from JSON on completion.
 */
export interface ServiceRequest {
    /**
     * uniquely (within the browser session) identifies this request.
     */
    readonly id: number,
    /**
     * A promise which will receive a ServiceResult when the request completes.
     */
    readonly promise: Promise<ServiceResponse>,
    /**
     * Cancels the service request.
     * @return true if the request was cancelled and had not already
     * completed in some other fashion.
     */
    cancel(): boolean,
    /**
     * Convert this result into a promise which is called back with an
     * instance of a requested type (optionally converted by the optional
     * converter function, which is passed an object returned by JSON.parse()).
     */
    result<T>(converter?: (obj: Object) => T): Promise<T>
}

/**
 * The raw response to a request.
 */
export interface ServiceResponse {
    /**
     * True if the response was >= 200 and <= 399.
     */
    readonly ok: boolean,
    /**
     * The HTTP response status code.
     */
    readonly status: number,
    /**
     * The headers (which, sadly, XMLHttpRequest returns as a string)
     */
    readonly headers: string,
    /**
     * Get the response text.  Per the contract of XMLHttpRequest, this
     * may throw an exception.
     */
    responseText: () => string,
    /**
     * Get the response text as JSON.
     */
    json: <T>(convert?: JSONConverter) => T | undefined,
}

export const QUERY_DEFAULTS: Partial<Query> = {
    timeout: 60000,
    headers: { Accept: 'application/json' },
    noCache: false,
}

function newSuccessResponse(xhr: XMLHttpRequest): ServiceResponse {
    return {
        ok: xhr.status >= 200 && xhr.status < 300,
        status: xhr.status,
        headers: xhr.getAllResponseHeaders(),
        // This is a method not a property because we want
        // to throw to the caller, not during construction when
        // we're trying to close out a future, and if xhr
        // is in the wrong state (errored), that is what will happen.
        // Theoretical, perhaps, but better safe than sorry.
        responseText: () => xhr.responseText,
        json: <T>(convert?: JSONConverter) => {
            const txt = xhr.responseText;
            return !txt
                ? undefined
                : (convert || DEFAULT_JSON_CONVERT)(JSON.parse(xhr.responseText)) as T;
        }
    };
}

type RequestMethod = 'get' | 'post' | 'put' | 'delete' | 'options'

class QueryImpl implements Query {
    readonly uri: string;
    readonly queryParams: object;
    readonly headers: object;
    readonly noCache: boolean;
    readonly timeout: number;

    constructor(query: Query) {
        this.uri = query.uri;
        this.queryParams = query.queryParams || {};
        this.headers = query.headers || {};
        this.noCache = query.noCache || false;
        this.timeout = query.timeout || 60000;
    }

    requestUri(): string {
        if (!this.queryParams) {
            return this.uri;
        }
        const queryString = Object.keys(this.queryParams)
            .map(k => encodeURIComponent(k) + '='
                + encodeURIComponent(this.queryParams[k]))
            .join('&')

        if (queryString) {
            let joinUsing: string
            if (this.uri.indexOf('?') >= 0) {
                joinUsing = '&'
            } else {
                joinUsing = '?'
            }
            return this.uri + joinUsing + queryString
        }
        return this.uri
    }

    configureRequest(req: XMLHttpRequest) {
        Object.keys(this.headers).forEach(key =>
            req.setRequestHeader(key, this.headers[key]))
        if (this.noCache) {
            req.setRequestHeader('Cache-Control', 'no-cache')
        }
        if (this.timeout > 0) {
            req.timeout = this.timeout
        }
    }
}
const DEFAULT_JSON_CONVERT: JSONConverter = <T>(rawObject: object) => {
    return rawObject as T;
}

/**
* A generic service client using nothing but XMLHttpRequest and JSON.parse().
*/
export class ServiceClient {
    private counter = 0;
    private readonly inFlight = new Map<number, ServiceRequest>();
    private readonly configurers: RequestConfigurer[] = [];
    private readonly listeners = new Set<ServiceClientListener>();

    constructor() {
    }

    private touch(requestId: number, message: ClientEvent, event?: any): void {
        const req = this.inFlight.get(requestId);
        const size = this.inFlight.size;
        try {
            this.listeners.forEach(lis => {
                lis(requestId, message, size, req, event);
            });
        } catch (err) {
            console.log("Notifying listeners", err);
        }
    }

    /**
     * Listen for events on any requests this client makes.
     * @param a listener
     * @return A function which will remove the attached listener
     */
    public listen(listener: ServiceClientListener): ListenerRemover {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Determine if this client has any requests in-progress.
     */
    public get hasInFlightRequests() {
        return this.inFlight.size > 0;
    }

    /**
     * IF you need to do custom authentication, set headers or otherwise
     * finddle with XMLHttpRequests on a global basis, pass a function
     * to do that here.  Note: Any listeners attached here will be clobbered.
     */
    public configureRequestsWith(cfig: RequestConfigurer): void {
        this.configurers.push(cfig);
    }

    private configureRequest(xhr: XMLHttpRequest) {
        this.configurers.forEach(cfig => cfig(xhr))
    }

    private static applyDefaults(query: Query): QueryImpl {
        return new QueryImpl({ ...QUERY_DEFAULTS, ...query })
    }

    private add(id: number, req: ServiceRequest) {
        this.inFlight[id] = req;
    }

    /**
     * Cancel all outbound requests (if any).
     */
    public cancelAll(): boolean {
        const hasAny = this.hasInFlightRequests;
        if (hasAny) {
            this.inFlight.forEach((req, id) => {
                req.cancel();
            });
        }
        return hasAny;
    }

    private completed(id: number): boolean {
        return this.inFlight.delete(id);
    }

    private cancelRequest(id: number, req: XMLHttpRequest): boolean {
        let wasTracked = this.completed(id);
        if (wasTracked) {
            req.abort();
        }
        return wasTracked;
    }

    /**
     * Perform an HTTP GET request.
     */
    public get(query) {
        return this.request('get', ServiceClient.applyDefaults(query));
    }

    /**
     * Perform an HTTP DELETE request.
     */
    public del(query) {
        return this.request('delete', ServiceClient.applyDefaults(query));
    }

    /**
     * Perform an HTTP POST request.
     */
    public post(query, payload) {
        return this.request('post', ServiceClient.applyDefaults(query), payload);
    }

    /**
     * Perform an HTTP DELETE request.
     */
    public options(query) {
        return this.request('options', ServiceClient.applyDefaults(query));
    }

    /**
     * Perform an HTTP PUT request.
     */
    public put<T>(query: Query, payload?: T): ServiceRequest {
        return this.request('put', ServiceClient.applyDefaults(query), payload);
    }

    private request<T>(method: RequestMethod, query: QueryImpl, payload?: T): ServiceRequest {
        const requestId = ++this.counter;

        const xhr = new XMLHttpRequest();

        // XXX make this an option, or do it in the generated client on authenticated
        // requests?  Required to get the minimal web api to pass basic auth credentials.
        xhr.withCredentials = true;

        const reqUri = query.requestUri();

        console.log("Invoke " + method + " to " + reqUri + " req id " + requestId);

        xhr.open(method, reqUri);

        query.configureRequest(xhr);

        if (payload && (method == 'put' || method == 'post')) {
            xhr.setRequestHeader('content-type', 'application/json');
        }
        this.configureRequest(xhr);
        const promise = new Promise<ServiceResponse>((resolve, reject) => {

            xhr.onload = evt => {
                this.touch(requestId, 'load', evt);
                this.completed(requestId);
                resolve(newSuccessResponse(xhr))
            }
            xhr.onerror = evt => {
                this.touch(requestId, 'error', evt);
                this.completed(requestId);
                reject(evt);
            }
            xhr.ontimeout = evt => {
                this.touch(requestId, 'timeout', evt);
                this.completed(requestId);
                reject(evt);
            }
            xhr.onabort = evt => {
                this.touch(requestId, 'abort', evt);
                this.completed(requestId);
                reject(evt);
            }
            // Listeners below are only so pretty progress
            // bars and such can be shown
            xhr.onloadstart = evt => {
                this.touch(requestId, 'loadstart', evt);
            }
            xhr.onloadend = evt => {
                this.touch(requestId, 'loadend', evt);
            }
            xhr.onprogress = evt => {
                this.touch(requestId, 'progress', evt);
            }
            xhr.onreadystatechange = evt => {
                this.touch(requestId, 'readystatechange', evt);
            }

            try {
                if (payload) {
                    let payloadJson = JSON.stringify(payload);
                    console.log("Send payload", payloadJson);
                    xhr.send(payloadJson);
                } else {
                    console.log("Send - no payload");
                    xhr.send();
                }
            } catch (err) {
                reject(err);
            }
        });
        const sc = this;
        let result = {
            id: requestId,
            promise: promise,
            cancel(): boolean {
                return sc.cancelRequest(requestId, xhr);
            },
            result<T>(converter?: (obj: Object) => T): Promise<T> {
                return new Promise<T>((resolve, reject): any => {
                    // Handle EVERY way things can go wrong - they
                    // are myriad
                    promise.catch(e => reject(e));
                    promise.then(res => {
                        let text: string;
                        try {
                            text = res.responseText();
                        } catch (err) {
                            return reject(err);
                        }
                        console.log("RAW RESPONSE", text);
                        if (!res.ok) {
                            return reject(res);
                        }
                        let obj: object;
                        try {
                            obj = JSON.parse(text);
                        } catch (err) {
                            return reject(err);
                        }
                        console.log("JSON RESPONSE", obj);
                        let result: T;
                        try {
                            result = (converter || DEFAULT_JSON_CONVERT)(obj)
                        } catch (err) {
                            return reject(err);
                        }
                        console.log("CONVERTED RESPONSE " + typeof result, result);
                        resolve(result)
                    });
                });
            }
        }
        this.add(requestId, result);
        return result;
    }
}

const CLIENT = new ServiceClient();
/**
 * Get the default service client instance.
 */
export function serviceClient(): ServiceClient {
    return CLIENT;
}
