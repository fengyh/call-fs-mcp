/*
 * Copyright [2022] [https://www.xiaonuo.vip]
 *
 * Snowy采用APACHE LICENSE 2.0开源协议，您在使用过程中，需要注意以下几点：
 *
 * 1.请不要删除和修改根目录下的LICENSE文件。
 * 2.请不要删除和修改Snowy源码头部的版权声明。
 * 3.本项目代码可免费商业使用，商业使用请保留源码和相关描述文件的项目出处，作者声明等。
 * 4.分发源码时候，请注明软件出处 https://www.xiaonuo.vip
 * 5.不可二次分发开源参与同类竞品，如有想法可联系团队xiaonuobase@qq.com商议合作。
 * 6.若您的项目无法满足以上几点，需要更多功能代码，获取Snowy商业授权许可，请在官网购买授权，地址为 https://www.xiaonuo.vip
 */
package vip.xiaonuo.fscloud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MQTT 连接配置
 *
 * @author fengyuhe
 * @date  2026/06/05
 */
@Data
@Component
@ConfigurationProperties(prefix = "fscloud.mqtt")
public class MqttProperties {

    /** MQTT Broker 地址, 注意这个是兜底的值, 实际使用时,
     * 会根据环境变量或配置文件中的值(application-{profile}.properties)进行覆盖 */
    private String brokerUrl = "tcp://localhost:1883";

    /** 客户端 ID */
    private String clientId = "snowy-fscloud";

    /** 订阅主题 */
    private String subTopic = "factory/sim/+/+/+";

    /** 连接超时（秒） */
    private int connectionTimeout = 10;

    /** 保活间隔（秒） */
    private int keepAliveInterval = 30;
}
