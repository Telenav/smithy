
/**
* Raw interface for creating a DOM element of some type.
*/
type BaseFactory = (id: string, name?: string) => HTMLElement;
const columnStyles = /col-.*/;

/**
* Factory which creates a dom element of some sort, and which can
* be wrappered to set up default attributes or styles.
*/
interface ElementFactory {
    create(id: string, name?: string): HTMLElement;
    withAttribute(name: string, value: string): ElementFactory;
    withStyles(style: string | string[]): ElementFactory;
}

export type StaticTextElementKind =
    'span'
    | 'div'
    | 'h1'
    | 'h2'
    | 'h3'
    | 'h4'
    | 'pre'
    | 'code';

export type ButtonElementKind = 'span' | 'div' | 'button';

/**
* Base class for element factories which use a BaseFactory under the
* hood.
*/
abstract class ElementFactoryBase implements ElementFactory {
    readonly factory: BaseFactory;

    constructor(factory: BaseFactory) {
        this.factory = factory;
    }

    create(id: string, name?: string): HTMLElement {
        return this.factory(id, name);
    }

    abstract withStyles(style: string | string[]): ElementFactory;
    abstract withAttribute(name: string, value: string): ElementFactory;
}

/**
* A complete simple element factory.
*/
class SimpleElementFactory extends ElementFactoryBase {

    constructor(factory: BaseFactory) {
        super(factory);
    }

    withAttribute(name: string, value: string): ElementFactory {
        return new AttributeWrapperElementFactory(this, name, value);
    }

    withStyles(style: string | string[]): ElementFactory {
        let cl = typeof style === 'string' ? [style] : style;
        return new StyleWrapperElementFactory(this, cl);
    }
}

function arrayOf<T>(itemOrItems: T | T[]) {
    if (Array.isArray(itemOrItems)) {
        return itemOrItems as T[];
    }
    return [itemOrItems];
}

/**
* An element factory which delegates to another to create the initial
* DOM element, and modifies its output in some fashion..
*/
abstract class WrapperElementFactory implements ElementFactory {
    private readonly delegate: ElementFactory;

    constructor(delegate: ElementFactory) {
        this.delegate = delegate;
    }

    abstract configure(el: HTMLElement): HTMLElement

    create(id: string, name?: string): HTMLElement {
        return this.configure(this.delegate.create(id, name));
    }

    abstract withStyles(style: string | string[]): ElementFactory;
    abstract withAttribute(name: string, value: string): ElementFactory;
}

class StyleWrapperElementFactory extends WrapperElementFactory {
    private readonly classes: string[];
    constructor(delegate: ElementFactory, classes: string[]) {
        super(delegate);
        this.classes = classes;
    }

    withAttribute(name: string, value: string): ElementFactory {
        return new AttributeWrapperElementFactory(this, name, value);
    }

    withStyles(style: string | string[]): ElementFactory {
        return new StyleWrapperElementFactory(this, arrayOf(style));
    }

    configure(el: HTMLElement): HTMLElement {
        this.classes.forEach(cl => {
            el.classList.add(cl);
        });
        return el;
    }
}

class AttributeWrapperElementFactory extends WrapperElementFactory {
    private readonly key: string;
    private readonly value: string;
    constructor(delegate: ElementFactory, key: string, value: string) {
        super(delegate);
        this.key = key;
        this.value = value;
    }
    withAttribute(name: string, value: string): ElementFactory {
        return new AttributeWrapperElementFactory(this, name, value);
    }
    withStyles(style: string | string[]): ElementFactory {
        return new StyleWrapperElementFactory(this, arrayOf(style));
    }
    configure(el: HTMLElement): HTMLElement {
        el.setAttribute(this.key, this.value);
        return el;
    }
}

function simpleEF(elementType: string, nameAttribute?: string): SimpleElementFactory {
    return new SimpleElementFactory((id, name): HTMLElement => {
        let result: HTMLElement = document.createElement(elementType);
        result.id = id;
        if (name && nameAttribute) {
            result.setAttribute(nameAttribute, name);
        }
        return result;
    });
}

// SimpleElementFactories for various kinds of common dom elements,
// and variants on <input>:
const INPUT = simpleEF("input", "name")
const LABEL = simpleEF("label", "for")
const SPAN = simpleEF("span")
const DIV = simpleEF("div")
const SELECT = simpleEF("select")
const H1 = simpleEF("h1")
const H2 = simpleEF("h2")
const H3 = simpleEF("h3")
const H4 = simpleEF("h4")
const PRE = simpleEF("pre")
const CODE = simpleEF("code")
const IFRAME = simpleEF("iframe")

function staticTextFactory(type: StaticTextElementKind): SimpleElementFactory {
    switch (type) {
        case 'div':
            return DIV;
        case 'span':
            return SPAN;
        case 'h1':
            return H1;
        case 'h2':
            return H2;
        case 'h3':
            return H3;
        case 'h4':
            return H4;
        case 'pre':
            return PRE;
        case 'code':
            return CODE;
    }
}

// Specialized variants of INPUT for different input types:
const TEXT = INPUT.withAttribute("type", "text");
const CHECKBOX = INPUT.withAttribute("type", "checkbox");
const DATETIME = INPUT.withAttribute("type", "datetime-local");
const EMAIL = INPUT.withAttribute("type", "email");
const NUMBER = INPUT.withAttribute("type", "number");
const RADIO = INPUT.withAttribute("type", "radio");
const RANGE = INPUT.withAttribute("type", "range");
const BUTTON = INPUT.withAttribute("type", "button");

// Basic interface for a ui component
interface HTMLComponent {
    element: Element;
    id: string;
    enabled: boolean;
    attach(to: string | HTMLComponent | Element): this;
    detach(): this;
    dispose(): this;
    withAttribute(name: string, value: string): this;
}

export type EventName = 'change' | 'blur' | 'focus';

export interface EventType<T> {
    name: EventName;
}

interface Convertible<T> {
    convert(obj: any): T;
}

export interface Transform<T, R> {
    toValue(value: T): R;
    fromValue(value: R): T;
    transform<X>(xform: Transform<R, X>): Transform<T, X>
}

interface Transformable<T> {
    transform<X>(xform: Transform<T, X>): TransformedEventType<T, X>;
}

class EventTypeImpl<T> implements EventType<T>, Convertible<T>, Transformable<T> {
    public readonly name: EventName;
    private readonly converter?: (any) => T;
    constructor(name: EventName, converter?: (any) => T) {
        this.name = name;
        this.converter = converter;
    }

    transform<X>(xform: Transform<T, X>): TransformedEventType<T, X> {
        return new TransformedEventType<T, X>(this, xform);
    }

    convert(obj: any): T {
        if (this.converter) {
            return this.converter(obj);
        }
        return obj as T;
    }
}

abstract class ChainableTransform<A, B> implements Transform<A, B> {
    transform<X>(xform: Transform<B, X>): Transform<A, X> {
        return new ChainedTransform<A, B, X>(this, xform);
    }
    abstract fromValue(value: B): A;
    abstract toValue(value: A): B;
}

class ChainedTransform<A, B, C> extends ChainableTransform<A, C> {
    private readonly first: Transform<A, B>;
    private readonly second: Transform<B, C>;
    constructor(first: Transform<A, B>, second: Transform<B, C>) {
        super();
        this.first = first;
        this.second = second;
    }
    transform<X>(xform: Transform<C, X>): Transform<A, X> {
        return new ChainedTransform<A, C, X>(this, xform);
    }
    fromValue(value: C): A {
        let b: B = this.second.fromValue(value);
        return this.first.fromValue(b);
    }
    toValue(value: A): C {
        let b: B = this.first.toValue(value);
        return this.second.toValue(b);
    }
}

class StringToStringListTransform extends ChainableTransform<string, string[]> {
    fromValue(value: string[]): string {
        console.log("StringToStringListTransform fromValue " + typeof value
            + " to " + value.join(","), value);
        return value.join(',');
    }

    toValue(value: string): string[] {
        console.log("StringToStringListTransform toValue " + typeof value
            + " to " + value, value.split(/\s*,\s*/));
        return value.split(/\s*,\s*/);
    }
}

class ListMemberTransform<T, R> extends ChainableTransform<T[], R[]> {
    private readonly xform: Transform<T, R>;
    constructor(xform: Transform<T, R>) {
        super();
        this.xform = xform;
    }

    fromValue(value: R[]): T[] {
        const result: T[] = [];
        value.forEach(val => {
            result.push(this.xform.fromValue(val));
        });
        return result;
    }

    toValue(value: T[]): R[] {
        const result: R[] = [];
        value.forEach(val => {
            result.push(this.xform.toValue(val));
        });
        return result;
    }
}

class StringToIntTransform extends ChainableTransform<string, number> {

    fromValue(value: number): string {
        return value.toString();
    }

    toValue(value: string): number {
        return parseInt(value);
    }
}

class StringToFloatTransform extends ChainableTransform<string, number> {

    fromValue(value: number): string {
        return value.toString();
    }

    toValue(value: string): number {
        return parseFloat(value);
    }
}

const STRING_TO_STRING_ARRAY = new StringToStringListTransform();
const STRING_TO_INT = new StringToIntTransform();
const STRING_TO_FLOAT = new StringToFloatTransform();
const STRING_ARRAY_TO_FLOAT_ARRAY = STRING_TO_STRING_ARRAY.transform(new ListMemberTransform(STRING_TO_FLOAT));
const STRING_ARRAY_TO_INT_ARRAY = STRING_TO_STRING_ARRAY.transform(new ListMemberTransform(STRING_TO_INT));


class TransformedEventType<T, R> implements EventType<R>, Transformable<R>, Convertible<R>  {
    private readonly delegate: EventType<T> & Convertible<T>;
    private readonly xform: Transform<T, R>;

    constructor(orig: EventType<T> & Convertible<T>, xform: Transform<T, R>) {
        this.delegate = orig;
        this.xform = xform;
    }

    transform<X>(xform: Transform<R, X>): TransformedEventType<R, X> {
        return new TransformedEventType<R, X>(this, xform);
    }

    get name(): EventName {
        return this.delegate.name;
    }

    convert(obj: any): R {
        let origResult: T = this.delegate.convert(obj);
        return this.xform.toValue(origResult);
    }
}

/// Define some generic event types with conversion methods
const FocusChangeInternal: EventTypeImpl<boolean> = new EventTypeImpl<boolean>('focus');
const TextChangeInternal: EventTypeImpl<string> = new EventTypeImpl<string>('change');
const IntegerChangeInternal: EventTypeImpl<number> = new EventTypeImpl<number>('change', val => {
    return typeof val === 'number' ? val as number : !val ? val : parseInt(val.toString());
});
const FloatChangeInternal: EventTypeImpl<number> = new EventTypeImpl<number>('change', val => {
    return typeof val === 'number' ? val as number : !val ? val : parseFloat(val.toString());
});
const DateTimeChangeInternal: EventTypeImpl<Date> = new EventTypeImpl<Date>('change', val => {
    return val instanceof Date ? val as Date : !val ? val : new Date(Date.parse(val.toString()));
});
const SelectChangeInternal: EventTypeImpl<boolean> = new EventTypeImpl<boolean>('change');

// Export them only as the raw EventType, not exposing conversion methods
export const FocusChange: EventType<boolean> = FocusChangeInternal;
export const TextChange: EventType<string> = TextChangeInternal;
export const SelectChange: EventType<boolean> = SelectChangeInternal;
export const IntegerChange: EventType<number> = IntegerChangeInternal;
export const FloatChange: EventType<number> = FloatChangeInternal;
export const DateTimeChange: EventType<Date> = DateTimeChangeInternal;

export type Listener<T> = (event: EventType<T>, id: string, newValue: T) => void;

export interface InteractiveComponent<T> extends HTMLComponent {
    listen(listener: Listener<T>): this;
    rawValue(): any;
    value(): T;
    isUnset(): boolean;
    onFocusChange(listener: Listener<boolean>): this;
    withStyles(style: string | string[]): this;
    setValue(value: any): this;
}

/**
* Thing which maintains an internal set of css classes that should be present
* and should *not* be present on a component's element, which can sync the
* component to the desired state, and preserve that state across the DOM element
* being replaced with a new one.
*/
class StyleUpdater {
    private readonly styles: Map<string, boolean> = new Map<string, boolean>();
    private readonly fetch: () => HTMLElement | null | undefined;
    constructor(fetch: () => HTMLElement | null | undefined) {
        this.fetch = fetch;
    }

    private target(): string {
        let e = this.fetch();
        return e ? e['id'] : '--';
    }

    addStyle(style: string) {
        this.styles.set(style, true);
    }

    removeStyle(style: string) {
        this.styles.set(style, false);
    }

    clearColumnStyles() {
        this.styles.forEach((val, style) => {
            if (columnStyles.test(style)) {
                this.styles.set(style, false);
            }
        });
    }

    sync() {
        let e = this.fetch();
        if (e) {
            this._sync(e);
        }
    }

    _sync(el: HTMLElement) {
        if (this.styles.size === 0) {
            return;
        }
        let cur: Set<string> = this.currentStyles();
        let l: DOMTokenList = el.classList;
        let toRemove = new Set<string>();
        l.forEach(style => {
            if (typeof this.styles.get(style) === 'undefined') {
                return;
            }
            if (!this.styles[style]) {
                toRemove.add(style);
            } else {
                cur.delete(style);
            }
        });
        toRemove.forEach(rem => {
            l.remove(rem);
        });
        cur.forEach(add => {
            l.add(add);
        });
    }

    currentStyles(): Set<string> {
        let result = new Set<string>();
        this.styles.forEach((val, key) => {
            if (val) {
                result.add(key);
            }
        });
        return result;
    }

    static stylesOn(el: HTMLElement): Set<string> {
        let result = new Set<string>();
        el.classList.forEach(item => {
            result.add(item);
        });
        return result;
    }
}

/**
* A UI component ... in the componenty sense.  Is able to realize and manipulate
* a single DOM element.
*/
class Component implements HTMLComponent {

    private factory: ElementFactory;
    public readonly id: string;
    private readonly name?: string;
    protected el: HTMLElement | null | undefined;
    protected readonly styles = new StyleUpdater(() => this.el);
    private attached = false;
    private title?: string;
    private enabledValue: boolean = true;

    constructor(factory: ElementFactory, id: string, name?: string) {
        this.factory = factory;
        this.id = id;
        this.name = name;
    }

    private checkRealized(): void {
        if (this.el) {
            throw new Error("Cannot change attributes after component is realized.");
        }
    }

    get enabled(): boolean {
        return this.enabledValue;
    }

    set enabled(val: boolean) {
        if (val !== this.enabledValue) {
            this.enabledValue = val;
            this.internalOnEnabledChanged(val);
        }
    }

    private internalOnEnabledChanged(to: boolean) {
        if (this.el) {
            this.el['disabled'] = !to;
        }
        this.onEnabledChanged(to);
    }

    protected onEnabledChanged(to: boolean) {
        // do nothing
    }

    public tooltip(): string | undefined {
        return this.title;
    }

    public isAttached(): boolean {
        return this.el ? true : false;
    }

    public setTooltip(title: string): this {
        this.title = title;
        if (this.el) {
            this.el.setAttribute("title", title);
        }
        return this;
    }

    clearColumnStyles() {
        this.styles.clearColumnStyles();
    }

    syncStyles(): void {
        this.styles.sync();
    }

    withStyles(style: string | string[]): this {
        let s = typeof style === 'string' ? [style] : style;
        if (s.length > 0) {
            s.forEach(sty => {
                this.styles.addStyle(sty);
            });
            this.syncStyles();
        }
        return this;
    }

    withoutStyles(style: string | string[]): this {
        if (this.el) {
            let s = typeof style === 'string' ? [style] : style;
            if (s.length > 0) {
                s.forEach(sty => {
                    this.styles.removeStyle(sty);
                });
                this.syncStyles();
            }
        }
        return this;
    }

    withAttribute(name: string, value: string): this {
        this.checkRealized();
        this.factory = this.factory.withAttribute(name, value);
        return this;
    }

    private changeAttached(to: boolean) {
        if (this.attached != to) {
            this.attached = to;
            return true;
        }
        return false;
    }

    private internalOnElementCreated(e: HTMLElement) {
        //        e.addEventListener(what???
        this.onElementCreated(e);
        if (this.title) {
            e.setAttribute('title', this.title);
        }
        if (!this.enabled) {
            e['disabled'] = true;
        }
        this.styles._sync(e);
    }

    protected onElementCreated(_e: HTMLElement) {
        // do nothing
    }

    protected onBeforeAttach(_e: HTMLElement) {
        // do nothing
    }

    protected onAfterAttach(_e: HTMLElement) {
        // do nothing
    }

    protected onBeforeDetach(_e: HTMLElement) {
        // do nothing
    }

    protected onAfterDetach(_e: HTMLElement) {
        // do nothing
    }

    protected onBeforeDispose(_e: HTMLElement) {
    }

    detach(): this {
        if (this.changeAttached(false)) {
            if (this.el && this.el.parentElement) {
                this.onBeforeDetach(this.el.parentElement);
                this.el.parentElement?.removeChild(this.el);
                this.onAfterDetach(this.el.parentElement);
            }
        }
        return this;
    }

    attach(to: string | Component | HTMLElement): this {
        let e: HTMLElement | null;
        if (typeof to === 'string') {
            let e1: HTMLElement | null = document.getElementById(to);
            if (e1 === null) {
                throw new Error("No element " + to + " to attach to");
            }
            e = e1;
        } else if (to instanceof Element) {
            e = to;
        } else {
            e = (to as Component).element;
        }
        if (this.el && e === this.el.parentElement) {
            return this;
        }
        if (!this.changeAttached(true)) {
            this.detach();
        }
        this.changeAttached(true);
        this.onBeforeAttach(e);
        e.appendChild(this.element);
        this.onAfterAttach(e);
        return this;
    }

    get element(): HTMLElement {
        if (!this.el) {
            let e = this.factory.create(this.id, this.name);
            this.internalOnElementCreated(e);
            this.el = e;
        }
        return this.el;
    }

    dispose(): this {
        if (this.el) {
            this.detach();
            this.onBeforeDispose(this.el);
            this.el = null;
        }
        return this;
    }
}


let iframes = 0;
export class IFrame extends Component {
    constructor(src : string, id? : string) {
        super(IFRAME.withAttribute("src", src).withStyles('sizeless'), id || ("iframe-" + iframes++));
    }
}

let staticTextIds = 1;
export class StaticText extends Component {
    private _text: string;
    constructor(text: string, type?: StaticTextElementKind, id?: string) {
        super(staticTextFactory(type || 'span'), id ? id : ("st-" + staticTextIds++));
        this._text = text;
    }

    get text() {
        return this._text;
    }

    set text(what: string) {
        this._text = what;
        if (this.el) {
            this.el.innerText = what;
        }
    }

    onElementCreated(e: HTMLElement) {
        e.innerText = this._text;
        super.onElementCreated(e);
    }
}

abstract class InputComponentBase<T> extends Component implements InteractiveComponent<T> {

    private readonly listeners: Listener<T>[];
    private readonly focusListeners: Listener<boolean>[];
    protected readonly eventType: EventTypeImpl<T>;
    protected previousValue?: any;

    constructor(eventType: EventTypeImpl<T>, factory: ElementFactory, id: string, name?: string) {
        super(factory, id, name);
        this.listeners = [];
        this.focusListeners = [];
        this.eventType = eventType;
    }

    public labeledWith(label: string): LabeledComponent<T> {
        return new LabeledComponent(this, label);
    }

    protected onChange(evt: any): void {
        if (!this.listeners) {
            console.log("No listeners field???", evt);
            console.log("I should be an instance of InputComponentBase but I am", this);
            throw new Error("Called with wrong `this`.");
        }
        this.listeners.forEach(lis => {
            lis(this.eventType, 'change', this.eventType.convert(this.rawValue()))
        });
        this.previousValue = this.rawValue();
    }

    isUnset(): boolean {
        let v = this.rawValue();
        return v === "" || v === null || typeof v === 'undefined';
    }

    value(): T {
        return this.eventType.convert(this.rawValue());
    }

    rawValue(): any {
        if (!this.el) {
            return this.previousValue || null;
        }
        return this.el.getAttribute("value");
    }

    protected focusChanged(gained: boolean, evt: any): void {
        this.focusListeners.forEach(lis => {
            lis(FocusChangeInternal, 'focus', gained);
        });
    }

    onFocusChange(lis: Listener<boolean>): this {
        this.focusListeners.push(lis);
        return this;
    }

    protected beginListening(e: HTMLElement) {
        //        e.onchange = evt => this.onChange(evt);
        e.addEventListener('change', evt => {
            this.onChange(evt);
        });
    }

    public setValue(value: any): this {
        this.previousValue = value;
        if (this.el) {
            this.setValueOn(value, this.el);
        }
        return this;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['value'] = value.toString();
    }

    onElementCreated(e: HTMLElement) {
        // Do NOT do e.onchange = this.onChange
        // or you get called with the wrong "this".
        this.beginListening(e);
        e.onfocus = evt => {
            this.focusChanged(true, evt)
        };
        e.onblur = evt => {
            this.focusChanged(false, evt)
        };
    }

    onAfterAttach(e: HTMLElement) {
        if (this.el && typeof this.previousValue != 'undefined') {
            this.setValueOn(this.previousValue, this.el);
        }
    }

    listen(listener: Listener<T>): this {
        this.listeners.push(listener);
        return this;
    }
}

function boolFromAny(value: any): boolean {
    return typeof value === 'boolean' ? value
        : typeof value === 'number' ? value !== 0
            : typeof value === 'string' ? 'true' === value
                : false;
}

export class Checkbox extends InputComponentBase<boolean> {

    constructor(id: string, name?: string) {
        super(SelectChangeInternal, CHECKBOX, id, name);
    }

    rawValue(): any {
        return this.el ? this.el['checked'] : false;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['checked'] = boolFromAny(value);
    }
}

export class RadioButton extends InputComponentBase<boolean> {

    constructor(id: string, group: string) {
        super(SelectChangeInternal, RADIO, id, group);
    }

    rawValue(): any {
        return this.el ? this.el['checked'] : false;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['checked'] = boolFromAny(value);
    }
}

function stringFromAny(value: any): string {
    return typeof value === 'string' ? value as string
        : '' + value;
}

export class TextField extends InputComponentBase<string> {

    constructor(id: string, name?: string) {
        super(TextChangeInternal, TEXT, id, name);
    }

    rawValue(): any {
        return this.el ? this.el['value'] : "";
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['value'] = stringFromAny(value);
    }
}
/*
const STRING_TO_STRING_ARRAY = new StringToStringListTransform();
const STRING_TO_INT = new StringToIntTransform();
const STRING_TO_FLOAT = new StringToFloatTransform();
const STRING_ARRAY_TO_FLOAT_ARRAY = STRING_TO_STRING_ARRAY.transform(new ListMemberTransform(STRING_TO_FLOAT));
const STRING_ARRAY_TO_INT_ARRAY = STRING_TO_STRING_ARRAY.transform(new ListMemberTransform(STRING_TO_INT));

*/
export function stringListField(id: string): TransformedTextField<string[]> {
    return new TransformedTextField<string[]>(id, STRING_TO_STRING_ARRAY);
}

export function integerListField(id: string): TransformedTextField<number[]> {
    return new TransformedTextField<number[]>(id, STRING_ARRAY_TO_INT_ARRAY);
}

export function floatListField(id: string): TransformedTextField<number[]> {
    return new TransformedTextField<number[]>(id, STRING_ARRAY_TO_FLOAT_ARRAY);
}

export class TransformedTextField<T> extends InputComponentBase<T> {
    private readonly xform: Transform<string, T>;

    constructor(id: string, xform: Transform<string, T>, name?: string) {
        super(TextChangeInternal.transform(xform), TEXT, id, name);
        this.xform = xform;
    }

    rawValue(): any {
        let result = this.el ? this.el['value'] : "";
        console.log("XFormed tf raw value", result);
        //        console.log("My value", this.value());
        return result;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        console.log("Set value on xf ", value);
        el['value'] = stringFromAny(value);
    }
}

export class IntegerField extends InputComponentBase<number> {

    constructor(id: string, name?: string) {
        super(IntegerChangeInternal, NUMBER, id, name);
    }

    rawValue(): any {
        let val = this.el ? this.el['value'] : 0;
        if (typeof val === 'string') {
            return parseInt(val);
        }
        return val;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['value'] = stringFromAny(value);
    }

    isUnset(): boolean {
        if (!this.el) {
            return true;
        }
        if (typeof this.el['value'] !== 'undefined' || '' === this.el['value']) {
            return true;
        }
        return false;
    }
}

export class FloatField extends InputComponentBase<number> {

    constructor(id: string, name?: string) {
        super(FloatChangeInternal, NUMBER, id, name);
    }

    rawValue(): any {
        let val = this.el ? this.el['value'] : 0;
        if (typeof val === 'string') {
            return parseFloat(val);
        }
        return val;
    }

    isUnset(): boolean {
        if (!this.el) {
            return true;
        }
        if (typeof this.el['value'] !== 'undefined' || '' === this.el['value']) {
            return true;
        }
        return false;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['value'] = stringFromAny(value);
    }
}

export class Slider extends InputComponentBase<number> {

    constructor(id: string, name?: string) {
        super(IntegerChangeInternal, RANGE, id, name);
    }

    withValueLabel(): InteractiveComponent<number> & Component & Labeled {
        let v = super.value();
        let lab = new LabeledComponent(this, v + "");
        // (event: EventType<T>, id: string, newValue: T) => void;
        super.listen((type, id, v1) => {
            console.log("ON CHANGE SLIDER ", v1);
            lab.setLabel(v1 + "");
        });
        return lab;
    }

    rawValue(): any {
        let val = this.el ? this.el['value'] : 0;
        if (typeof val === 'string') {
            return parseFloat(val);
        }
        return val;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        el['value'] = stringFromAny(value);
    }
}

function dateFromAny(value: any): Date {
    let d: Date;
    if (typeof value === 'object') {
        if (value instanceof Date) {
            d = value;
        } else {
            d = new Date(0);
        }
    } else if (typeof value === 'number') {
        d = new Date(value as number);
    } else if (typeof value === 'string') {
        d = new Date(Date.parse(value as string));
    } else {
        d = new Date(0);
    }
    return d;
}

function dateToLocalDateTimeIsoString(d: Date) {
    const offsetTime = d.getTime() - (d.getTimezoneOffset() * 60000);
    if (isNaN(offsetTime)) {
        console.log("Passed invalid date; use unix epoch", d);
        d = new Date(0);
    }
    const result = new Date(offsetTime).toISOString();
    // assume Z is the suffix
    return result.substring(0, result.length - 1);
}

export class DateTimePicker extends InputComponentBase<Date> {

    constructor(id: string, name?: string, value?: Date) {
        super(DateTimeChangeInternal, DATETIME, id, name);
        if (value) {
            super.previousValue = dateToLocalDateTimeIsoString(value);
        }
    }

    rawValue(): any {
        let val = this.el ? this.el['value'] : '2022-12-08T23:48Z';
        console.log("Raw date value " + (typeof val), val);
        return val;
    }

    protected setValueOn(value: any, el: HTMLElement) {
        let d = dateFromAny(value);
        const nue: string = dateToLocalDateTimeIsoString(dateFromAny(value));
        console.log("Convert to " + nue, d.toISOString());
        el['value'] = nue;
    }

}

export class Label extends Component {

    private text: string;
    constructor(text: string, id: string, labelFor: string) {
        super(LABEL, id, labelFor);
        this.text = text;
    }

    public setLabel(txt: string) {
        this.text = txt;
        if (this.el) {
            this.el.innerHTML = txt;
        }
    }

    onElementCreated(el: Element) {
        el['innerHTML'] = this.text;
    }
}

export function labelFor(label: string, labelFor: Component) {
    return new Label(label, labelFor.id + "Label", labelFor.id);
}

abstract class Container extends Component {

    protected children: Array<Component> = new Array<Component>();

    constructor(inline: boolean, id: string, name?: string) {
        super(inline ? SPAN : DIV, id, name);
    }

    onEnabledChanged(enabled: boolean) {
        this.children.forEach(ch => ch.enabled = enabled);
    }

    private addChildRealized(comp: Component) {
        comp.attach(this);
    }

    private removeChildRealized(comp: Component) {
        comp.dispose();
    }

    public setTooltip(title: string): this {
        super.setTooltip(title);
        this.children.forEach(child => {
            child.setTooltip(title);
        });
        return this;
    }

    protected addChild(comp: Component) {
        this.children.push(comp);
        if (this.el) {
            this.addChildRealized(comp);
        }
    }

    protected removeChild(comp: Component) {
        const index = this.children.indexOf(comp, 0);
        if (index > -1) {
            this.children.splice(index, 1);
        }
        if (this.el != null) {
            this.removeChildRealized(comp);
        }
    }

    onElementCreated(el: HTMLElement) {
        this.children.forEach(ch => {
            ch.attach(el);
        });
    }

    onBeforeDetach() {
        this.children.forEach(ch => {
            ch.detach();
        });
    }

    onBeforeDispose() {
        this.children.forEach(ch => {
            ch.dispose();
        });
    }
}

export interface Labeled {
    setLabel(newLabel: string): this;
    labelComponent(): Label;
}

export class LabeledComponent<T> extends Container implements InteractiveComponent<T>, Labeled {

    readonly proxying: InteractiveComponent<T> & Component;
    constructor(delegate: InteractiveComponent<T> & Component, label: string) {
        super(true, delegate.id + "WithLabel");
        this.proxying = delegate;
        super.addChild(labelFor(label, delegate));
        super.addChild(delegate);
    }

    isUnset(): boolean {
        return this.proxying.isUnset();
    }

    setValue(value: any): this {
        this.proxying.setValue(value);
        return this;
    }

    labelComponent(): Label {
        for (let i = 0; i < this.children.length; i++) {
            if (this.children[i] === this.proxying) {
                continue;
            }
            return this.children[i] as Label;
        }
        throw new Error("No label component present");
    }

    public setLabel(lbl: string): this {
        let comp = this.children[0] as Label;
        comp.setLabel(lbl);
        return this;
    }

    public delegate(): InteractiveComponent<T> & Component {
        return this.proxying;
    }

    public setTooltip(title: string): this {
        super.setTooltip(title);
        this.children.forEach(child => {
            child.setTooltip(title);
        });
        return this;
    }

    onFocusChange(listener: Listener<boolean>): this {
        this.proxying.onFocusChange(listener);
        return this;
    }

    value(): T {
        return this.proxying.value();
    }
    rawValue() {
        return this.proxying.rawValue();
    }

    listen(listener: Listener<T>): this {
        this.proxying.listen(listener);
        return this;
    }
}

export class Row extends Container {
    static counter = 0;

    constructor() {
        super(false, ('row-' + (++Row.counter)));
        let st = Row.counter % 2 === 0 ? 'row-even' : 'row-odd';
        super.withStyles(['row', st]);
    }

    syncStyles() {
        for (let i = 0; i < this.children.length; i++) {
            let child: Component = this.children[i];
            child.clearColumnStyles();
            let st = ['col-' + (i + 1), 'column'];
            child.withStyles(st);
        }
        super.syncStyles();
    }

    onBeforeAttach(e: HTMLElement) {
        super.onBeforeAttach(e);
        this.syncStyles();
    }

    public addChild(comp: Component) {
        if (this.el) {
            this.syncStyles();
        }
        super.addChild(comp);
    }

    public removeChild(comp: Component) {
        if (this.el) {
            this.syncStyles();
        }
        super.addChild(comp);
    }
}

export class ComboBox extends InputComponentBase<string> {

    private items: string[];
    constructor(id: string, ...items: string[]) {
        super(TextChangeInternal, SELECT, id, id);
        this.items = items;
    }

    onElementCreated(e: HTMLElement) {
        let vals: string[] = [];
        this.items.forEach(item => {
            vals.push('<option value="' + item + '">' + item + '</option>');
        });
        e.innerHTML = vals.join('');
        super.onElementCreated(e);
    }

    rawValue(): any {
        if (!this.el) {
            return "";
        }
        let e = this.el;
        return e['options'][e['selectedIndex']]['text'] as string;
    }
}

export interface Clickable {
    onClick(lis: (any) => void): void;
}

function factoryFrom(kind: boolean | ButtonElementKind | ElementFactory): ElementFactory {
    console.log("Factory from " + typeof kind, kind);
    if (typeof kind === 'boolean') {
        if (kind) {
            return SPAN;
        } else {
            return BUTTON;
        }
    } else if (typeof kind === 'object') {
        return kind as ElementFactory;
    } else if (typeof kind === 'string') {
        switch (kind) {
            case 'span':
                return SPAN;
            case 'div':
                return DIV;
            case 'button':
                return BUTTON;
        }
    }
    return BUTTON;
}

export class Button extends Component implements Clickable {
    private readonly listeners: Array<(v: any) => void>
        = new Array<(v: any) => void>();
    private text: string;

    constructor(kind: boolean | ButtonElementKind | ElementFactory, id: string, text: string) {
        super(factoryFrom(kind), id, id);
        this.text = text;
    }

    public onClick(lis: (v: any) => void): Button {
        this.listeners.push(lis);
        return this;
    }

    private clicked(evt: any) {
        this.listeners.forEach(l => l(evt))
    }

    public setText(text: string) {
        this.text = text;

    }

    protected setTextOn(text: string, el: HTMLElement) {
        switch (el.tagName) {
            case 'input':
                el['value'] = this.text;
                break;
            default:
                el.innerText = this.text;
        }
    }

    onElementCreated(el: HTMLElement) {
        this.setTextOn(this.text, el);
        el['value'] = this.text;
        el.onclick = evt => {
            this.clicked(evt)
        };
    }
}

export class Panel extends Container {
    static counter = 0;
    constructor() {
        super(false, ('panel-' + (++Panel.counter)));
        let st = Row.counter % 2 === 0 ? 'panel-even' : 'panel-odd';
        super.withStyles(['panel', st]);
    }

    public addChild(comp: Component) {
        if (this.el) {
            this.syncStyles();
        }
        super.addChild(comp);
    }

    public removeChild(comp: Component) {
        if (this.el) {
            this.syncStyles();
        }
        super.addChild(comp);
    }
}

export class NavPanel extends Container {
    private readonly panels: Map<string, Panel>
        = new Map<string, Panel>();
    private readonly labels: Map<string, Button>
        = new Map<string, Button>();
    private readonly containerId: string;
    private defaultItem?: string;
    private active: string | undefined | null;
    private readonly listeners: ((label: string, panel: Panel) => void)[] = [];
    constructor(id: string, containerId: string) {
        super(false, id);
        this.containerId = containerId;
        super.withStyles("nav");
    }

    /**
    * Listen for changes in the currently selected panel.
    */
    public listen(f: (label: string, panel: Panel) => void): this {
        this.listeners.push(f);
        return this;
    }

    /**
    * Get the currently selected panel.
    */
    get selected(): Panel | undefined {
        if (!this.active) {
            return;
        }
        return this.panels.get(this.active);
    }

    public add(label: string, panel: Panel) {
        if (!this.defaultItem) {
            this.defaultItem = label;
        }
        this.panels.set(label, panel);
        const lbl = new Button(true, this.id + "_" + label, label)
            .withStyles("nav-item");
        lbl.onClick(evt => {
            this.activate(label);
        });
        this.labels.set(label, lbl);
        super.addChild(lbl);
    }

    private announce(label: string, panel: Panel) {
        this.listeners.forEach(lis => lis(label, panel));
    }

    public activate(which: string) {
        if (which === this.active) {
            return;
        }
        if (this.active) {
            this._deactivate(this.active);
        }
        const pnl = this.panels.get(which);
        if (pnl) {
            pnl.attach(this.containerId);
            const btn = this.labels.get(which);
            btn?.withStyles('nav-active');
            this.active = which;
            this.announce(which, pnl);
        }
    }

    public deactivate() {
        if (this.active) {
            this._deactivate(this.active);
        }
    }

    private _deactivate(which: string) {
        if (which === this.active) {
            const pnl = this.panels.get(which);
            const btn = this.labels.get(which);
            pnl?.dispose();
            btn?.withoutStyles('nav-active');
            this.active = null;
        }
    }

    onElementCreated(e: HTMLElement) {
        super.onElementCreated(e);
        if (this.defaultItem) {
            this.activate(this.defaultItem);
        }
    }
}

export type ListComponentFactory<V> = (index: number, v: V, of: number) => Component;

export class ListPanel<V> extends Container {
    private readonly itemFactory: ListComponentFactory<V>;
    private first: boolean = true;
    protected values: Array<V>;

    constructor(id: string, factory: ListComponentFactory<V>, values?: Array<V>) {
        super(false, id);
        super.withStyles("list-panel");
        this.itemFactory = factory;
        this.values = values || [];
    }

    protected createElement(index: number, value: V, len: number): Component {
        let v = this.values[index];
        let styles = ['list-item', index % 2 === 0 ? 'list-item-even' : 'list-item-odd'];
        let c = this.itemFactory(index, v, len).withStyles(styles);
        return c;
    }

    onElementCreated(e: HTMLElement) {
        super.onElementCreated(e);
        this.first = false;
        const len = this.values.length;
        for (let i = 0; i < len; i++) {
            let c = this.createElement(i, this.values[i], len);
            super.addChild(c);
            c.attach(e);
        }
    }

    onBeforeDispose() {
        this.first = true;
    }

    public items(): V[] {
        return [...this.values];
    }
}

export interface Problem {
    description: string;
    inPath: string;
}

export class ProblemsPanel extends Component {
    private problems: Problem[] = [];
    constructor(id: string) {
        super(DIV.withStyles('problems'), id);
    }

    public clear() {
        this.problems = [];
        if (this.el != null) {
            this.el['innerHTML'] = '';
        }
        console.log("CLEAR PROBLEMS");
    }

    public add(inPath: string, description: string) {
        this.addProblem({ inPath, description });
    }

    public addProblem(problem: Problem) {
        this.problems.push(problem);
        this.onAdd(problem);
    }

    private onAdd(problem: Problem) {
        if (this.el) {
            this.addOne(problem, this.el);
        }
    }

    private addOne(problem: Problem, el: HTMLElement) {
        const oneProblem = document.createElement("div");
        oneProblem.classList.add("problem");
        const target = document.createElement("span");
        target.classList.add("problemtarget");
        target.innerHTML = problem.inPath;

        const em = document.createElement("span");
        em.innerHTML = " ┉ ";

        const desc = document.createElement("span");
        desc.classList.add("problemdesc");
        desc.innerHTML = problem.description;
        oneProblem.appendChild(desc);
        oneProblem.appendChild(em);
        oneProblem.appendChild(target);
        el.appendChild(oneProblem);
    }

    private update(el: HTMLElement) {
        this.clear();
        this.problems.forEach(prob => {
            this.addOne(prob, el);
        });
    }

    onElementCreated(el: HTMLElement) {
        this.update(el);
        super.onElementCreated(el);
    }
}

export interface Startable {
    running: boolean;
    start(): this;
    stop(): this;
}

//const spinnerText = '▁▂▃▄▅▆▇█▉▊▋▌▌▍▎▍▌▋▊▉█▇▆▅▄▃▂▁';
//const spinnerText = '▫◽◻□◻◽▫▱';
const spinnerText = '☰☴☶☷☳☱☰☲☵☰☱☲☴☰☶☳☰';
const spinnerInterval = 150;
export class Spinner extends Component implements Startable {
    private counter: number = 0;
    private timeout: any | null | undefined;
    private active = false;

    constructor(id: string) {
        super(SPAN.withStyles("spinner"), id);
    }

    get running(): boolean {
        return this.active;
    }

    set running(val: boolean) {
        if (val != this.active) {
            if (val) {
                this.start();
            } else {
                this.stop();
            }
        }
    }

    public start(): this {
        if (this.active) {
            return this;
        }
        this.active = true;
        if (this.el) {
            this.startTimer();
        }
        return this;
    }

    private clearText() {
        if (this.el) {
            this.el.innerHTML = ' ';
        }
    }

    public stop(): this {
        let t = this.timeout;
        if (t) {
            this.timeout = null;
            clearInterval(t);
        }
        this.active = false;
        return this;
    }


    onBeforeDispose() {
        stop();
    }

    private startTimer() {
        this.timeout = setInterval(() => {
            this.tick();
        }, spinnerInterval);
    }

    private tick() {
        if (this.el) {
            this.el.innerHTML = this.curr();
        }
    }

    private curr(): string {
        return spinnerText.charAt((this.counter++ % spinnerText.length));
    }

    onElementCreated(el: HTMLElement) {
        super.onElementCreated(el);
        if (this.active) {
            this.startTimer();
        }
    }

}
