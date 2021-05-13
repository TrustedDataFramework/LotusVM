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
                args: o.action.args == null ? [] : o.action.args.map(x => `${x.type}:${x.value}`),
            }
            if(o.expected && o.expected.length) {
                f["return"] = `${o.expected[0].type}:${o.expected[0].value}`
            }
            currentModule.tests.push(
                f
            )
        }

        if(o.type === "assert_trap") {
            let f = {
                "function": o.action.field,
                args: o.action.args == null ? [] : o.action.args.map(x => `${x.type}:${x.value}`),
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