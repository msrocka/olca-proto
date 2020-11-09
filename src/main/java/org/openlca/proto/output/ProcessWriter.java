package org.openlca.proto.output;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import org.openlca.core.database.ProcessDao;
import org.openlca.core.model.Process;
import org.openlca.core.model.ProcessType;
import org.openlca.core.model.SocialAspect;
import org.openlca.core.model.Version;
import org.openlca.proto.Proto;
import org.openlca.util.Strings;

public class ProcessWriter {

  private final WriterConfig config;

  public ProcessWriter(WriterConfig config) {
    this.config = config;
  }

  public Proto.Process write(Process process) {
    var proto = Proto.Process.newBuilder();
    if (process == null)
      return proto.build();

    // root entity fields
    proto.setType("Process");
    proto.setId(Strings.orEmpty(process.refId));
    proto.setName(Strings.orEmpty(process.name));
    proto.setDescription(Strings.orEmpty(process.description));
    proto.setVersion(Version.asString(process.version));
    if (process.lastChange != 0L) {
      var instant = Instant.ofEpochMilli(process.lastChange);
      proto.setLastChange(instant.toString());
    }

    // categorized entity fields
    if (Strings.notEmpty(process.tags)) {
      Arrays.stream(process.tags.split(","))
        .filter(Strings::notEmpty)
        .forEach(proto::addTags);
    }
    if (process.category != null) {
      proto.setCategory(Refs.toRef(process.category, config));
    }

    // model specific fields
    proto.setProcessType(processType(process));
    proto.setDefaultAllocationMethod(allocationType(process));
    proto.setInfrastructureProcess(process.infrastructureProcess);
    if (process.location != null) {
      proto.setLocation(Refs.toRef(process.location, config));
    }
    proto.setLastInternalId(process.lastInternalId);

    // DQ systems
    if (process.dqSystem != null) {
      proto.setDqSystem(Refs.toRef(process.dqSystem, config));
    }
    proto.setDqEntry(Strings.orEmpty(process.dqEntry));
    if (process.exchangeDqSystem != null) {
      proto.setExchangeDqSystem(
        Refs.toRef(process.exchangeDqSystem, config));
    }
    if (process.socialDqSystem != null) {
      proto.setSocialDqSystem(
        Refs.toRef(process.socialDqSystem, config));
    }

    // parameters
    var paramWriter  = new ParameterWriter(config);
    for (var param : process.parameters) {
      proto.addParameters(paramWriter.write(param));
    }

    writeExchanges(process, proto);
    writeSocialAspects(process, proto);

    return proto.build();
  }

  private Proto.ProcessType processType(Process p) {
    if (p == null || p.processType == null)
      return Proto.ProcessType.UNDEFINED_PROCESS_TYPE;
    return p.processType == ProcessType.LCI_RESULT
      ? Proto.ProcessType.LCI_RESULT
      : Proto.ProcessType.UNIT_PROCESS;
  }

  private Proto.AllocationType allocationType(Process p) {
    if (p == null || p.defaultAllocationMethod == null)
      return Proto.AllocationType.UNDEFINED_ALLOCATION_TYPE;
    switch (p.defaultAllocationMethod) {
      case CAUSAL:
        return Proto.AllocationType.CAUSAL_ALLOCATION;
      case ECONOMIC:
        return Proto.AllocationType.ECONOMIC_ALLOCATION;
      case PHYSICAL:
        return Proto.AllocationType.PHYSICAL_ALLOCATION;
      default:
        return Proto.AllocationType.UNDEFINED_ALLOCATION_TYPE;
    }
  }

  private void writeExchanges(Process p, Proto.Process.Builder proto) {
    for (var e : p.exchanges) {
      var pe = Proto.Exchange.newBuilder();
      pe.setAvoidedProduct(e.isAvoided);
      pe.setInput(e.isInput);
      if (e.baseUncertainty != null) {
        pe.setBaseUncertainty(e.baseUncertainty);
      }
      pe.setAmount(e.amount);
      pe.setAmountFormula(Strings.orEmpty(e.formula));
      pe.setDqEntry(Strings.orEmpty(e.dqEntry));
      pe.setDescription(Strings.orEmpty(e.description));
      pe.setCostFormula(Strings.orEmpty(e.costFormula));
      if (e.costs != null) {
        pe.setCostValue(e.costs);
      }
      if (e.currency != null) {
        pe.setCurrency(Refs.toRef(e.currency, config));
      }
      pe.setInternalId(e.internalId);
      if (e.uncertainty != null) {
        pe.setUncertainty(Util.uncertainty(e.uncertainty));
      }

      // default provider
      if (e.defaultProviderId > 0) {
        var provider = new ProcessDao(config.db)
          .getDescriptor(e.defaultProviderId);
        if (provider != null) {
          pe.setDefaultProvider(Refs.toProcessRef(provider));
        }
      }

      // flow references
      if (e.flow != null) {
        pe.setFlow(Refs.toFlowRef(e.flow, config));
      }
      if (e.flowPropertyFactor != null) {
        var fp = e.flowPropertyFactor.flowProperty;
        if (fp != null) {
          pe.setFlowProperty(Refs.toRef(fp));
        }
      }
      if (e.unit != null) {
        pe.setUnit(Refs.toRef(e.unit));
      }

      if (Objects.equals(e, p.quantitativeReference)) {
        pe.setQuantitativeReference(true);
      }

      proto.addExchanges(pe);
    }
  }

  private void writeSocialAspects(Process p, Proto.Process.Builder proto) {
    for (var aspect : p.socialAspects) {
      var pa = Proto.SocialAspect.newBuilder();
      if (aspect.indicator != null) {
        pa.setSocialIndicator(Refs.toRef(aspect.indicator, config));
      }
      pa.setComment(Strings.orEmpty(aspect.comment));
      pa.setQuality(Strings.orEmpty(aspect.quality));
      pa.setRawAmount(Strings.orEmpty(aspect.rawAmount));
      pa.setActivityValue(aspect.activityValue);
      pa.setRiskLevel(riskLevel(aspect));
      if (aspect.source != null) {
        pa.setSource(Refs.toRef(aspect.source, config));
      }
      proto.addSocialAspects(pa);
    }
  }

  private Proto.RiskLevel riskLevel(SocialAspect aspect) {
    if (aspect == null || aspect.riskLevel == null)
      return Proto.RiskLevel.UNDEFINED_RISK_LEVEL;
    try {
      return Proto.RiskLevel.valueOf(aspect.riskLevel.name());
    } catch (Exception e) {
      return Proto.RiskLevel.UNDEFINED_RISK_LEVEL;
    }
  }
}
