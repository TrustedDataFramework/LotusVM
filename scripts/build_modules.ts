#!/usr/bin/env node -r ts-node/register
import path = require('path')
import fs = require('fs')

const dir = path.join(__dirname, '../src/test/resources/spec-official')

const modules = []
for(let f of fs.readdirSync(dir)) {
    if(!f.endsWith(".json") || f === 'modules.json')
        continue
    let p = path.join(dir, f)
    const o = JSON.parse(fs.readFileSync(p, 'utf-8'))
    for(let m of wastJson2Modules(o)) {
        modules.push(m)
    }
    fs.writeFileSync(path.join(dir, 'modules.json'), JSON.stringify(modules, null, 2))
}

interface Command {
    type: "module" | "assert_return" | "assert_trap" | "assert_malformed",
    line: number,
    filename?: string
    action? : {
        type: "invoke",
        field: "string",
        args: {type: "i32" | "i64" | "f32" | "f64", value: "string"}[],
    }
    expected: {type: "i32" | "i64" | "f32" | "f64", value: "string"}[]
    text?: string
}

interface TestModule {
    file: string,
    tests: {
        "function": string,
        args?: string[],
        trap?: string
        "return": string
    }[]
}

interface WastJson {
    commands: Command[]
}

function arg2hex(x: {type: "i32" | "i64" | "f32" | "f64", value: string}): string {
    let v = /^[0-9]+$/.test(x.value) ? ('0x' + BigInt(x.value).toString(16)) : x.value;
    return `${v.startsWith('0x') ? 'i64' : x.type}:${v}`
}

function wastJson2Modules(wast: WastJson): TestModule[] {
    const r = []
    let currentModule = null

    for (let o of wast.commands) {
        if (o.type === "module") {
            if (currentModule !== null) {
                r.push(currentModule)
            }
            currentModule = {
                file: o.filename,
                tests: []
            }
        }

        if(o.type === "assert_return") {
            let f = {
                "function": o.action.field,
                args: o.action.args == null ? [] : o.action.args.map(arg2hex),
            }
            if(o.expected && o.expected.length) {
                f["return"] = arg2hex(o.expected[0])
            }
            currentModule.tests.push(
                f
            )
        }

        if(o.type === "assert_trap") {
            let f = {
                "function": o.action.field,
                args: o.action.args == null ? [] : o.action.args.map(arg2hex),
            }
            f["trap"] = o.text
            currentModule.tests.push(
                f
            )
        }
    }

    if(currentModule != null)
        r.push(currentModule)
    return r
}