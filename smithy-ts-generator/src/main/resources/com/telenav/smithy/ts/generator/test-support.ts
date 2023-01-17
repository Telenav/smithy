import * as Assert from 'assert';
import { Validatable } from '$SERVICE_MODEL';

// A quick and dirty microframework for running tests of generated types
// Note that this frameworkd does NOT use throwing to indicate failure.
//
// If tests subsequent to one that fails should not be run, use TestSuite.chain,
// and call stopOnFirstFailure() for it.

export type FailureOutput = string | number | boolean | object;
/**
* Code that performs a test of some sort, and calls `onProblem` with any problems
* that should cause the test to fail.
*/
export type Test<T> = (desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) => void;


function applyMap<K, V>(from: Map<K, V[]> | undefined, onProblem: (path: K, problem: V) => void) {
    if (!from) {
        return;
    }
    from.forEach((v, k) => {
        v.forEach(prob => {
            onProblem(k, prob);
        });
    });
}

function addToMap<K, V>(path: K, problem: V, problems?: Map<K, V[]>): void {
    if (!problems) {
        return;
    }
    let items = problems.get(path);
    if (!items) {
        items = [];
        problems.set(path, items);
    }
    items.push(problem);
}

/** A thing that accepts test code to run it against some input.
*/
export type TestCollection<T> = (test: Test<T>) => void;
export type TestAdder<T> = (coll: TestCollection<T>) => void;
export type TestResults = Array<Map<string, string[]>>;
export type InputWithDescription<T> = [string, T];

/**
* A collection of tests, possibly of multiple types, that can run all of them
* and produce some output.
*/
export class TestSuite {
    readonly tests: Array<(desc: string) => Map<string, FailureOutput[]> | void>;
    readonly name: string;

    public constructor(name?: string) {
        this.name = name ? name : "Tests";
        this.tests = new Array<(desc: string) => Map<string, FailureOutput[]> | void>();
    }

    /**
    * Create a chain of test methods, all of which will be passed a collection of inputs.
    * Optionally, the chain can be set to abort further tests on the first failure.
    */
    public chain<T>(c: (chain: TestChain<T>) => void, name: string): (...inputs: InputWithDescription<T>[]) => void {
        let tc = new TestChain<T>(name);
        c(tc);
        return (inputs => {
            this.add(clo => {
                clo(tc.test.bind(tc));
            }, inputs);
        });
    }

    /**
    * Run all of the tests in the suite, returning non-void in the case that there
    * are failures.
    */
    public run(): Array<Map<string, FailureOutput[]>> | void {
        let results = new Array<Map<string, FailureOutput[]>>();
        this.tests.forEach(test => {
            let res = test(this.name);
            if (res) {
                results.push(res);
            }
        });
        if (results.length > 0) {
            return results;
        }
    }

    /**
    * Add a test and a collection of inputs for it.
    */
    public add<T>(testAdder: TestAdder<T>, ...inputs: InputWithDescription<T>[]): TestSuite {
        const tests = new Array<Test<T>>();
        testAdder(test => tests.push(test));
        this.tests.push(desc => {
            const results: Map<string, FailureOutput[]> = new Map<string, FailureOutput[]>();
            tests.forEach(t => {
                inputs.forEach(inp => {
                    let count = 0;
                    t(inp[0], inp[1], (path, prob) => {
                        let res: object = { 'inputDescription': inp[0] };
                        res['input'] = inp[1];
                        let inRes: object = {};
                        inRes[desc] = prob;
                        res[desc] = inRes;
                        addToMap(path, res, results);
                        count++;
                    });
                });
            });
            if (results.size > 0) {
                return results;
            } else {
                if (desc != "Tests") {
                    console.log(`OK: ${desc}`);
                }
            }
        });
        return this;
    }
}

/**
* A test chain allows multiple `Test<T>` functions to be run against the
* same input, in some sequence.  Optionally, it can be told not to run further
* tests after any failure by calling `stopOnFirstFailure()`.
*/
export class TestChain<T> {

    public readonly name: string;
    private readonly tests: Array<Test<T>> = new Array<Test<T>>();
    private stop: boolean;

    public constructor(name: string) {
        this.name = name;
        this.stop = false;
    }

    /**
    * If this is called on the chain before its first run, then if any test
    * fails, subsequent ones will be skipped for this chain (but other chains
    * in the suite will still run).
    */
    public stopOnFirstFailure(): TestChain<T> {
        this.stop = true;
        return this;
    }

    get size(): number {
        return this.tests.length;
    }

    /**
    * Add a test.
    */
    public add(test: Test<T>): TestChain<T> {
        this.tests.push(test);
        return this;
    }

    /**
    * Implementation of Test so it can be added to a suite.
    */
    public test(desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) {
        let results = this.run(desc, input);
        if (results) {
            applyMap(results, onProblem);
        }
    }

    public run(desc: string, input: T): Map<string, FailureOutput[]> | void {
        let problems: Map<string, FailureOutput[]> = new Map<string, FailureOutput[]>();
        this.tests.forEach(test => {
            if (this.stop && problems.size > 0) {
                return;
            }
            let pth = '';
            let theInput: any = '';
            try {
                test(this.name, input, (path: string, problem: FailureOutput) => {
                    pth = path;
                    theInput = input;
                    let res: object = { 'input': input, 'problem': problem };
                    addToMap(path, res, problems);

                });
            } catch (err) {
                let out = { 'desc': desc, 'thrown': err, 'input': theInput };
                out[(err as any)['message']] = err;
                addToMap(pth, out, problems);
            }
        });
        if (problems.size > 0) {
            return problems;
        } else {
            console.log(`OK: ${this.name}: ${desc}`);
        }
    }
}

function runValidatable(msg: string, v: Validatable): Map<string, FailureOutput[]> | void {
    let problems: Map<string, string[]> = new Map<string, string[]>();
    v.validate(msg, (pth, prob) => {
        let items = problems.get(pth);
        if (!items) {
            items = [];
            problems.set(pth, items);
        }
        items.push(prob);
    });
    if (problems.size > 0) {
        return problems;
    }
}

/**
* Returns a test of the validate() method on a Validatable type, which requires that
* it find no problems.
*/
export function expectValid<T extends Validatable>(): Test<T> {
    return (desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) => {
        let r = runValidatable(desc, input);
        if (r) {
            applyMap(r, onProblem);
        }
    }
}

/**
 * Returns a test of the validate() method on a Validatable type, which requires that
 * it at least one problem.
 */
export function expectInvalid<T extends Validatable>(): Test<T> {
    return (desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) => {
        let r = runValidatable(desc, input);
        if (!r) {
            onProblem(desc, { "Input should have been invalid": input });
        }
    }
}

/**
 * Returns a test that the value passed as the *expected* argument matches that returned
 * by the passed retrieval function which operates on an instance of the input type T.
 */
export function expectEqual<T, R>(msg: string, expected: R, convert: (t: T) => R): Test<T> {
    return (desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) => {
        let val: R = convert(input);
        if (val !== expected) {
            let out = { msg: { expected: expected, got: val }, input: input };
            onProblem(desc, out);
        }
    }
}

interface HasToJSON {
    toJSON(): any
}

/**
 * Returns a test that the set of keys on the object returned by `oJSON()` on type `T` is
 * the set of keys passed - this is used to determine if the `@jsonName` trait is being
 * handled correctly.
 */
export function expectToJsonKeys<T extends HasToJSON>(msg: string, ...keys: string[]): Test<T> {
    let keySet = {};
    keys.forEach(key => keySet[key] = true);
    return (desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) => {
        let o = input.toJSON();
        let got = {};
        Object.keys(o).forEach(key => got[key] = true);
        let out = { expected: keySet, got: got, message: msg };
        try {
            Assert.deepEqual(got, keySet);
        } catch (err) {
            out['problem'] = "Set of keys on output of toJSON does not match";
            onProblem(desc, out);
        }
    };
}

/**
 * Returns a test that the object returned by converting the input argument of type `T`
 * to JSON and back results in an object that deeply equals the original.
 */
export function jsonConvertible<T extends Object>(f: (a: any) => T): Test<T> {
    return (desc: string, input: T, onProblem: (path: string, problem: FailureOutput) => void) => {

        let out = {
            input: input,
        };
        if (typeof input['toJSON'] === 'function') {
            let f = (input['toJSON']) as (() => any);
            f.bind(input)();
        }

        let json = JSON.stringify(input);

        if (typeof json === 'undefined') {
            out['problem'] = "Undefined returned by toJSON/JSON.stringify()";
            onProblem(desc, out);
            return;
        }

        let obj;
        try {
            obj = JSON.parse(json);
        } catch (err) {
            out['problem'] = "Unparsable output from JSON.parse()";
            out['json'] = json;
            out['error'] = err;
            onProblem(desc, out);
            return;
        }

        let deserialized: T;
        try {
            deserialized = f(obj);
            out['deserialized'] = deserialized;
        } catch (err) {
            out['problem'] = "fromJSON threw";
            out['json'] = json;
            out['error'] = err;
            onProblem(desc, out);
            return;
        }
        if (obj !== input) {
            try {
                Assert.deepEqual(deserialized, input);
            } catch (err) {
                out['json'] = json;
                out['problem'] = "Deserialized not equal";
                onProblem(desc, out);
            }
        }
    };
}

export type MapConsumer<K, V> = (k: K, v: V) => MapConsumer<K, V>;
/**
  * Convenience mechanism for creating a map inline, since in typescript
  * that is rather clunky
  */
export function map<K, V>(f: (mb: MapConsumer<K, V>) => void): Map<K, V> {
    let result = new Map<K, V>();
    let fact = (k: K, v: V) => {
        result.set(k, v);
        return fact;
    };
    f(fact);
    return result;
}
