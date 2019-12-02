package org.tdf.lotusvm.section;


import lombok.Getter;
import lombok.Setter;
import org.tdf.lotusvm.BytesReader;
import org.tdf.lotusvm.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * The binary encoding of modules is organized into sections. Most sections correspond to one component of a
 * module record, except that function definitions are split into two sections, separating their type declarations in the
 * function section from their bodies in the code section.
 */
public class Module {
    // the magic number of wasm
    @Getter
    private int magic;
    // version of wasm binary
    @Getter
    private int version;

    @Getter
    private List<CustomSection> customSections = new ArrayList<>();

    @Getter
    private TypeSection typeSection;

    @Getter
    private ImportSection importSection;

    @Getter
    private FunctionSection functionSection;

    // In the current version of WebAssembly, at most one table may be defined or imported in a single module,
    // and all constructs implicitly reference this table 0. This restriction may be lifted in future versions.
    @Getter
    private TableSection tableSection;

    // In the current version of WebAssembly, at most one memory may be defined or imported in a single
    // module, and all constructs implicitly reference this memory 0. This restriction may be lifted in future versions.
    @Getter
    @Setter
    private MemorySection memorySection;

    @Getter
    private GlobalSection globalSection;

    @Getter
    private ExportSection exportSection;

    @Getter
    private StartSection startSection;

    @Getter
    private ElementSection elementSection;

    @Getter
    private CodeSection codeSection;

    @Getter
    private DataSection dataSection;

    public Module(byte[] binary) throws Exception {
        parse(binary);
    }

    private void parse(byte[] binary) throws Exception {
        BytesReader reader = new BytesReader(binary);
        magic = reader.readUint32();
        if (magic != Constants.MAGIC_NUMBER) throw new Exception("wasm: Invalid magic number");
        version = reader.readUint32();
        if (version != Constants.VERSION)
            throw new Exception(String.format("wasm: unknown binary version: %d", version));
        readSections(reader);
    }

    private void readSections(BytesReader reader) {
        SectionReader sectionReader = new SectionReader(reader);
        while (reader.remaining() > 0){
            if(reader.peek() < 0 || reader.peek() >= SectionID.values().length)
                throw new RuntimeException(String.format("unknown section type %x", reader.peek()));
            SectionID id = SectionID.values()[reader.peek()];
            switch (id) {
                case CUSTOM:
                    customSections.add(sectionReader.readSection(CustomSection.class));
                    break;
                case TYPE:
                    typeSection = sectionReader.readSection(TypeSection.class);
                    break;
                case IMPORT:
                    importSection = sectionReader.readSection(ImportSection.class);
                    break;
                case FUNCTION:
                    functionSection = sectionReader.readSection(FunctionSection.class);
                    break;
                case TABLE:
                    tableSection = sectionReader.readSection(TableSection.class);
                    break;
                case MEMORY:
                    memorySection = sectionReader.readSection(MemorySection.class);
                    break;
                case GLOBAL:
                    globalSection = sectionReader.readSection(GlobalSection.class);
                    break;
                case EXPORT:
                    exportSection = sectionReader.readSection(ExportSection.class);
                    break;
                case START:
                    startSection = sectionReader.readSection(StartSection.class);
                    break;
                case ELEMENT:
                    elementSection = sectionReader.readSection(ElementSection.class);
                    break;
                case CODE:
                    codeSection = sectionReader.readSection(CodeSection.class);
                    break;
                case DATA:
                    dataSection = sectionReader.readSection(DataSection.class);
                    break;
            }
        }
    }

}
