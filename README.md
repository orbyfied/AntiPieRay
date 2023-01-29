# AntiPieRay
Attempts to block usage of the F3 debug pie chart as an exploit for  
base finding by preventing players from rendering specific block entities
when they are invisible to the player.

### Technical Details
> **Injection**  
> When a player joins, the Netty channel used to communicate  
> with them from the server is injected with a custom packet handler 
> of type `PacketHandler` by the name `AntiPieRay_packet_handler`. 
> The packet handler is configured 
> to listen for writes of `ClientboundBlockEntityDataPacket` by the 
> server. When it is called, it will check if the packet should be cancelled.
>  
> **Check**  
> In order to determine if a packet should be cancelled, a few checks 
> are performed:
> * Check if the block entity type should be checked, this can 
> be specified in the configuration. if this check fails, the packet
> will be let through.
> * Check if the block entities center is within 15 blocks of the
> player.
> * Check if the block entity is visible to the player utilizing a custom
> ray cast algorithm you can find in `FastRayCast`.