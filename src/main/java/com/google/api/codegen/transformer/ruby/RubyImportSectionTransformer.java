/* Copyright 2017 Google Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.transformer.ruby;

import com.google.api.codegen.metacode.InitCodeNode;
import com.google.api.codegen.transformer.ImportSectionTransformer;
import com.google.api.codegen.transformer.MethodTransformerContext;
import com.google.api.codegen.transformer.SurfaceNamer;
import com.google.api.codegen.transformer.SurfaceTransformerContext;
import com.google.api.codegen.viewmodel.ImportFileView;
import com.google.api.codegen.viewmodel.ImportSectionView;
import com.google.api.codegen.viewmodel.ImportTypeView;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class RubyImportSectionTransformer implements ImportSectionTransformer {
  @Override
  public ImportSectionView generateImportSection(SurfaceTransformerContext context) {
    Set<String> importFilenames = generateImportFilenames(context);
    ImportSectionView.Builder importSection = ImportSectionView.newBuilder();
    importSection.standardImports(generateStandardImports());
    importSection.externalImports(generateExternalImports(context));
    importSection.appImports(generateAppImports(context, importFilenames));
    importSection.serviceImports(generateServiceImports(context, importFilenames));
    return importSection.build();
  }

  @Override
  public ImportSectionView generateImportSection(
      MethodTransformerContext context, Iterable<InitCodeNode> specItemNodes) {

    ImportSectionView.Builder importSection = ImportSectionView.newBuilder();
    importSection.standardImports(ImmutableList.<ImportFileView>of());
    importSection.externalImports(ImmutableList.<ImportFileView>of());
    importSection.appImports(generateSampleImports(context, specItemNodes));
    importSection.serviceImports(ImmutableList.<ImportFileView>of());
    return importSection.build();
  }

  private List<ImportFileView> generateSampleImports(
      MethodTransformerContext context, Iterable<InitCodeNode> specItemNodes) {
    SurfaceNamer namer = context.getNamer();
    Interface service = context.getInterface();
    Map<String, String> namespaces = new HashMap<>();

    // Add interface scope since the client will be used in the sample.
    namespaces.put(
        namer.getNamespace(service.getFile()), namer.getNamespaceNickname(service.getFile()));

    for (InitCodeNode node : specItemNodes) {
      TypeRef type = node.getType();
      boolean isMessage = type.isMessage();
      boolean isEnum = type.isEnum();
      if (isMessage || isEnum) {
        ProtoFile file = isMessage ? type.getMessageType().getFile() : type.getEnumType().getFile();
        namespaces.put(namer.getNamespace(file), namer.getNamespaceNickname(file));
      }
    }

    ImmutableList.Builder<ImportFileView> sampleImports = ImmutableList.builder();
    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
      if (entry.getKey().compareTo(entry.getValue()) != 0) {
        sampleImports.add(createAliasImport(entry.getValue(), entry.getKey()));
      }
    }
    return sampleImports.build();
  }

  private List<ImportFileView> generateStandardImports() {
    return ImmutableList.of(createImport("json"), createImport("pathname"));
  }

  private List<ImportFileView> generateExternalImports(SurfaceTransformerContext context) {
    ImmutableList.Builder<ImportFileView> imports = ImmutableList.builder();
    imports.add(createImport("google/gax"));

    if (context.getInterfaceConfig().hasLongRunningOperations()) {
      imports.add(createImport("google/gax/operation"));
      imports.add(createImport("google/longrunning/operations_client"));
    }

    return imports.build();
  }

  private List<ImportFileView> generateAppImports(
      SurfaceTransformerContext context, Set<String> filenames) {
    ImmutableList.Builder<ImportFileView> imports = ImmutableList.builder();
    for (String filename : filenames) {
      imports.add(createImport(context.getNamer().getProtoFileImportName(filename)));
    }
    return imports.build();
  }

  private List<ImportFileView> generateServiceImports(
      SurfaceTransformerContext context, Set<String> filenames) {
    ImmutableList.Builder<ImportFileView> imports = ImmutableList.builder();
    imports.add(createImport("google/gax/grpc"));
    for (String filename : filenames) {
      imports.add(createImport(context.getNamer().getServiceFileImportName(filename)));
    }
    return imports.build();
  }

  private Set<String> generateImportFilenames(SurfaceTransformerContext context) {
    Set<String> filenames = new TreeSet<>();
    filenames.add(context.getInterface().getFile().getSimpleName());
    for (Method method : context.getSupportedMethods()) {
      Interface targetInterface = context.asRequestMethodContext(method).getTargetInterface();
      filenames.add(targetInterface.getFile().getSimpleName());
    }
    return filenames;
  }

  private ImportFileView createImport(String name) {
    ImportFileView.Builder fileImport = ImportFileView.newBuilder();
    fileImport.moduleName(name);
    fileImport.types(ImmutableList.<ImportTypeView>of());
    return fileImport.build();
  }

  private ImportFileView createAliasImport(String nickname, String fullName) {
    ImportFileView.Builder fileImport = ImportFileView.newBuilder();
    fileImport.moduleName(fullName);
    fileImport.types(
        ImmutableList.of(
            ImportTypeView.newBuilder().fullName(fullName).nickname(nickname).build()));
    return fileImport.build();
  }
}
