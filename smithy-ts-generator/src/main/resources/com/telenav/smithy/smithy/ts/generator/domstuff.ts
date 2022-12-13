
type BaseFactory = (id: string, name?: string) => HTMLElement;
const columnStyles = /col-.*/;

interface ElementFactory {
    create(id: string, name?: string): HTMLElement;
    withAttribute(name: string, value: string): ElementFactory;
    withStyles(style: string | string[]): ElementFactory;
}

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

class SimpleElementFactory extends ElementFactoryBase {

    constructor(factory: BaseFactory) {
        super(factory);
    }

    withAttribute(name: string, value: string): ElementFactory {
        return new AttributeWrapperElementFactory(this, name, value);
    }

    withStyles(style: string | string[]): ElementFactory {
        let cl = typeof style == 'string' ? [style] : style;
        return new StyleWrapperElementFactory(this, cl);
    }
}

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
        let cl = typeof style == 'string' ? [style] : style;
        return new StyleWrapperElementFactory(this, cl);
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
        let cl = typeof style == 'string' ? [style] : style;
        return new StyleWrapperElementFactory(this, cl);
    }
    configure(el: HTMLElement): HTMLElement {
        el.setAttribute(this.key, this.value);
        return el;
    }
}

const INPUT = new SimpleElementFactory((id, name): HTMLElement => {
    let result: HTMLElement = document.createElement("input");
    result.id = id;
    if (name) {
        result.setAttribute("name", name);
    }
    return result;
});

const LABEL = new SimpleElementFactory((id, name): HTMLElement => {
    let result: HTMLElement = document.createElement("label");
    result.id = id;
    if (name) {
        result.setAttribute("for", name);
    }
    return result;
});

const SPAN = new SimpleElementFactory((id, name): HTMLElement => {
    let result: HTMLElement = document.createElement("span");
    result.id = id;
    if (name) {
        result.setAttribute("for", name);
    }
    return result;
});

const DIV = new SimpleElementFactory((id, name): HTMLElement => {
    let result: HTMLElement = document.createElement("div");
    result.id = id;
    if (name) {
        result.setAttribute("for", name);
    }
    return result;
});

const SELECT = new SimpleElementFactory((id, name): HTMLElement => {
    let result: HTMLElement = document.createElement("select");
    result.id = id;
    return result;
});

const TEXT = INPUT.withAttribute("type", "text");
const CHECKBOX = INPUT.withAttribute("type", "checkbox");
const DATETIME = INPUT.withAttribute("type", "datetime-local");
const EMAIL = INPUT.withAttribute("type", "email");
const NUMBER = INPUT.withAttribute("type", "number");
const RADIO = INPUT.withAttribute("type", "radio");
const RANGE = INPUT.withAttribute("type", "range");
const BUTTON = INPUT.withAttribute("type", "button");

interface Comp {
    element: Element;
    id: string;
    attach(to: string | Comp | Element): this;
    detach(): this;
    dispose(): this;
    withAttribute(name: string, value: string): this;
}

export type EventName = 'change' | 'blur' | 'focus';

export interface EventType<T> {
    name: EventName;
}

class EventTypeImpl<T> implements EventType<T> {
    public readonly name: EventName;
    private readonly converter?: (any) => T;
    constructor(name: EventName, converter?: (any) => T) {
        this.name = name;
        this.converter = converter;
    }

    convert(obj: any): T {
        if (this.converter) {
            return this.converter(obj);
        }
        return obj as T;
    }
}

const FocusChangeInternal: EventTypeImpl<boolean> = new EventTypeImpl<boolean>('focus');
const TextChangeInternal: EventTypeImpl<string> = new EventTypeImpl<string>('change');
const IntegerChangeInternal: EventTypeImpl<number> = new EventTypeImpl<number>('change', val => {
    return typeof val == 'number' ? val as number : !val ? val : parseInt(val.toString());
});
const FloatChangeInternal: EventTypeImpl<number> = new EventTypeImpl<number>('change', val => {
    return typeof val == 'number' ? val as number : !val ? val : parseFloat(val.toString());
});
const DateTimeChangeInternal: EventTypeImpl<Date> = new EventTypeImpl<Date>('change', val => {
    return val instanceof Date ? val as Date : !val ? val : new Date(Date.parse(val.toString()));
});

const SelectChangeInternal: EventTypeImpl<boolean> = new EventTypeImpl<boolean>('change');

export const FocusChange: EventType<boolean> = FocusChangeInternal;
export const TextChange: EventType<string> = TextChangeInternal;
export const SelectChange: EventType<boolean> = SelectChangeInternal;
export const IntegerChange: EventType<number> = IntegerChangeInternal;
export const FloatChange: EventType<number> = FloatChangeInternal;
export const DateTimeChange: EventType<Date> = DateTimeChangeInternal;

export type Listener<T> = (event: EventType<T>, id: string, newValue: T) => void;

interface InteractiveComponent<T> extends Comp {
    listen(listener: Listener<T>): this;
    rawValue(): any;
    value(): T;
    onFocusChange(listener: Listener<boolean>): this;
    withStyles(style: string | string[]): this;
    setValue(value: any);
}

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
        if (this.styles.size == 0) {
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

class Component implements Comp {

    private factory: ElementFactory;
    public readonly id: string;
    private readonly name?: string;
    protected el: HTMLElement | null | undefined;
    protected readonly styles = new StyleUpdater(() => this.el);
    private attached = false;
    private title?: string;
    constructor(factory: ElementFactory, id: string, name?: string) {
        this.factory = factory;
        this.id = id;
        this.name = name;
    }

    private checkRealized(): void {
        if (this.el) {
            throw new Error("Cannot change initial style or attributes after component is realized");
        }
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
            throw new Error("Called with wrong 'this'.");
        }
        this.listeners.forEach(lis => {
            lis(this.eventType, 'change', this.eventType.convert(this.rawValue()))
        });
        this.previousValue = this.rawValue();
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

    public setValue(value: any) {
        this.previousValue = value;
        if (this.el) {
            this.setValueOn(value, this.el);
        }
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
        super(true, delegate.id + "Label");
        this.proxying = delegate;
        super.addChild(labelFor(label, delegate));
        super.addChild(delegate);
    }

    setValue(value: any) {
        this.proxying.setValue(value);
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
        let st = Row.counter % 2 == 0 ? 'row-even' : 'row-odd';
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

interface Clickable {
    onClick(lis: (any) => void): void;
}

export enum ButtonKind {
    SPAN_BUTTON,
    DIV_BUTTON,
    BUTTON_BUTTON,
}

function factoryFrom(kind: boolean | ButtonKind | ElementFactory): ElementFactory {
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
            case 'SPAN_BUTTON':
                return SPAN;
            case 'DIV_BUTTON':
                return DIV;
            case 'BUTTON_BUTTON':
                return BUTTON;
        }
    }
    return BUTTON;
}

export class Button extends Component implements Clickable {
    private readonly listeners: Array<(v: any) => void>
        = new Array<(v: any) => void>();
    private text: string;

    constructor(kind: boolean | ButtonKind | ElementFactory, id: string, text: string) {
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
        let st = Row.counter % 2 == 0 ? 'panel-even' : 'panel-odd';
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
    constructor(id: string, containerId: string) {
        super(false, id);
        this.containerId = containerId;
        super.withStyles("nav");
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
    values: Array<V>;

    constructor(id: string, factory: ListComponentFactory<V>, values?: Array<V>) {
        super(false, id);
        super.withStyles("list-panel");
        this.itemFactory = factory;
        this.values = values || [];
    }

    protected createElement(index: number, value: V, len: number) : Component {
        let v = this.values[index];
        let styles = ['list-item', index % 2 == 0 ? 'list-item-even' : 'list-item-odd'];
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
