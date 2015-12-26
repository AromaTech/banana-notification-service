/*
 * Copyright 2015 Aroma Tech.
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

package tech.aroma.banana.notification.service.operations;

import javax.inject.Inject;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.aroma.banana.notification.service.pigeon.Pigeon;
import tech.aroma.banana.notification.service.pigeon.PigeonFactory;
import tech.aroma.banana.thrift.channels.BananaChannel;
import tech.aroma.banana.thrift.notification.service.SendNotificationRequest;
import tech.aroma.banana.thrift.notification.service.SendNotificationResponse;
import tech.sirwellington.alchemy.annotations.access.Internal;
import tech.sirwellington.alchemy.annotations.designs.patterns.StrategyPattern;
import tech.sirwellington.alchemy.thrift.operations.ThriftOperation;

import static tech.aroma.banana.notification.service.NotificationAssertions.validEvent;
import static tech.aroma.banana.thrift.functions.BananaAssertions.checkNotNull;
import static tech.aroma.banana.thrift.functions.BananaAssertions.withMessage;
import static tech.sirwellington.alchemy.annotations.designs.patterns.StrategyPattern.Role.CLIENT;
import static tech.sirwellington.alchemy.arguments.Arguments.checkThat;
import static tech.sirwellington.alchemy.arguments.assertions.Assertions.notNull;
import static tech.sirwellington.alchemy.arguments.assertions.CollectionAssertions.nonEmptyList;
import static tech.sirwellington.alchemy.generator.ObjectGenerators.pojos;

/**
 *
 * @author SirWellington
 */
@Internal

final class SendNotificationOperation implements ThriftOperation<SendNotificationRequest, SendNotificationResponse>
{

    private final static Logger LOG = LoggerFactory.getLogger(SendNotificationOperation.class);
    
    @StrategyPattern(role = CLIENT)
    private final PigeonFactory pigeonFactory;

    @Inject
    SendNotificationOperation(PigeonFactory pigeonFactory)
    {
        checkThat(pigeonFactory)
            .is(notNull());
        
        this.pigeonFactory = pigeonFactory;
    }
    
    
    @Override
    public SendNotificationResponse process(SendNotificationRequest request) throws TException
    {
        checkNotNull(request, "request is missing");
        
        checkThat(request.event)
            .throwing(withMessage("Invalid Event Type"))
            .is(validEvent());
        
        checkThat(request.channels)
            .throwing(withMessage("missing Channels to send notifications to"))
            .is(nonEmptyList());
        
        // For each Channel in the request...
        // Obtain a 'courier' or a 'pigeon' type from a factory.
        // Send the pigeon off to send the event
        //Each pigeon is responsible for sending the message to the right place
        int success = 0;
        for(BananaChannel channel : request.channels)
        {
            Pigeon<? super TBase> pigeon = pigeonFactory.getPigeonFor(channel);
            
            if (pigeon == null)
            {
                LOG.warn("Pigeon Factory returned null");
                continue;
            }
            
            LOG.debug("Sending pigeon {} to deliver event {}", pigeon, request.event);
            pigeon.deliverMessageTo(request.event, channel);
            success++;
        }
        
        LOG.debug("Sent event {} to {} channels", request.event, success);
        
        SendNotificationResponse response = pojos(SendNotificationResponse.class).get();
        
        return response;
    }

}
